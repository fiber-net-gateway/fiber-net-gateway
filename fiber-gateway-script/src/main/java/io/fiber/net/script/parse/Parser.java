/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fiber.net.script.parse;

import com.fasterxml.jackson.databind.JsonNode;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.Library;
import io.fiber.net.script.ast.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * copy spring
 */
public class Parser {
    static final String[] EMPTY_STR_ARR = Constant.EMPTY_STR_ARR;
    static final ExpressionNode[] EMPTY_NODE_ARR = new ExpressionNode[0];
    private static final Pattern VALID_QUALIFIED_ID_PATTERN = Pattern.compile("[\\p{L}\\p{N}_$]+");

    private final Library library;
    private boolean hasAssign = true;

    // The token stream constructed from that expression string
    private List<Token> tokenStream;

    // length of a populated token stream
    private int tokenStreamLength;

    // Current location in the token stream when processing tokens
    private int tokenStreamPointer;

    private final Map<String, DirectiveStatement> directiveMap = new HashMap<>();

    private static final Keyword[] KW_VALUES = Keyword.values();

    private static Keyword tryMatchKeyWords(String name) {
        for (Keyword kw : KW_VALUES) {
            if (kw.idt.equals(name)) {
                return kw;
            }
        }
        return null;
    }

    enum Keyword {
        LET, IF, ELSE, FOR, OF, CONTINUE, BREAK, RETURN, DIRECTIVE, FROM, TRY, CATCH, THROW;

        final String idt;


        Keyword() {
            idt = name().toLowerCase();
        }

        public String getIdt() {
            return idt;
        }

    }

    private Parser(Library library) {
        this.library = library;
    }

    public Parser(Library library, boolean hasAssign) {
        this.library = library;
        this.hasAssign = hasAssign;
    }

    public Block parseScript(String script) throws ParseException {
        Tokenizer tokenizer = new Tokenizer(script);
        tokenizer.process();
        return parseScriptFromTokens(tokenizer.getTokens(), script);
    }

    public Block parseScriptFromTokens(List<Token> tokens, String originScript) {
        this.tokenStream = tokens;
        this.tokenStreamLength = tokens.size();
        this.tokenStreamPointer = 0;
        Block block;
        try {
            block = eatBlock(false);
        } catch (ParseException e) {
            e.expressionString = originScript;
            throw e;
        }
        if (moreTokens()) {
            throw new ParseException(originScript, peekToken().startpos, SpelMessage.MORE_INPUT, toString(nextToken()));
        }
        return block;
    }

    public ExpressionNode parseExpression(String exp) throws ParseException {
        Tokenizer tokenizer = new Tokenizer(exp);
        tokenizer.process();
        return parseExpression(tokenizer.getTokens(), exp);
    }

    public ExpressionNode parseExpression(List<Token> tokens, String originExp) throws ParseException {
        this.tokenStream = tokens;
        this.tokenStreamLength = tokens.size();
        this.tokenStreamPointer = 0;
        ExpressionNode expressionNode;
        try {
            expressionNode = eatExpression();
        } catch (ParseException e) {
            e.expressionString = originExp;
            throw e;
        }
        if (moreTokens()) {
            throw new ParseException(originExp, peekToken().startpos, SpelMessage.MORE_INPUT, toString(nextToken()));
        }
        return expressionNode;
    }

    private Statement eatStatement() throws ParseException {
        Statement statement = tryEatIfStatement();
        if (statement != null) {
            return statement;
        }
        statement = tryEatForeachStatement();
        if (statement != null) {
            return statement;
        }

        statement = tryEatBreakStatement();
        if (statement != null) {
            return statement;
        }
        statement = tryEatContinueStatement();
        if (statement != null) {
            return statement;
        }
        statement = tryEatReturnStatement();
        if (statement != null) {
            return statement;
        }

        statement = tryEatThrowStatement();
        if (statement != null) {
            return statement;
        }

        statement = tryEatTryCatchStatement();
        if (statement != null) {
            return statement;
        }

        statement = tryEatVariableDeclareStatement();
        if (statement != null) {
            return statement;
        }

        statement = tryEatDirectiveStatement();
        if (statement != null) {
            return statement;
        }

        ExpressionNode eatExpression = eatExpression();
        peekToken(TokenKind.SEMICOLON, true);
        return new ExpressionStatement(eatExpression);
    }

    private Statement tryEatThrowStatement() {
        if (!moreTokens() || !peekIdentifierToken(Keyword.THROW.idt)) {
            return null;
        }
        Token token = nextToken();

        ExpressionNode node = eatExpression();
        peekToken(TokenKind.SEMICOLON, true);
        return new ThrowStatement(toPos(token.startpos, node.getEndPosition()), node);
    }

    private Statement tryEatTryCatchStatement() {
        if (!moreTokens() || !peekIdentifierToken(Keyword.TRY.idt)) {
            return null;
        }

        Token tryTk = eatKeyWord(Keyword.TRY);
        Block tryBk = eatBlock(true);
        eatKeyWord(Keyword.CATCH);
        eatToken(TokenKind.LPAREN);
        Identifier identifier = eatIdentifier();
        eatToken(TokenKind.RPAREN);
        Block catchBk = eatBlock(true);
        return new TryCatchStatement(toPos(tryTk.startpos, catchBk.getEndPosition()), identifier, tryBk, catchBk);
    }

    private ContinueStatement tryEatContinueStatement() {
        if (!moreTokens() || !peekIdentifierToken(Keyword.CONTINUE.idt)) {
            return null;
        }

        Token token = nextToken();
        peekToken(TokenKind.SEMICOLON, true);
        return new ContinueStatement(toPos(token));
    }

    private BreakStatement tryEatBreakStatement() {
        if (!moreTokens() || !peekIdentifierToken(Keyword.BREAK.idt)) {
            return null;
        }

        Token token = nextToken();
        peekToken(TokenKind.SEMICOLON, true);
        return new BreakStatement(toPos(token));
    }

    private ReturnStatement tryEatReturnStatement() {
        if (!moreTokens() || !peekIdentifierToken(Keyword.RETURN.idt)) {
            return null;
        }
        Token token = nextToken();

        if (peekToken(TokenKind.SEMICOLON, true)) {
            return new ReturnStatement(toPos(token), null);
        }

        ExpressionNode node = eatExpression();
        peekToken(TokenKind.SEMICOLON, true);
        return new ReturnStatement(toPos(token.startpos, node.getEndPosition()), node);
    }

    private DirectiveStatement tryEatDirectiveStatement() {
        if (!moreTokens() || !peekIdentifierToken(Keyword.DIRECTIVE.idt)) {
            return null;
        }
        int startpos = nextToken().startpos;

        Identifier name = eatIdentifier();

        if (!peekIdentifierToken(Keyword.FROM.idt)) {
            raiseInternalException(name.getEndPosition() + 1, SpelMessage.KEYWORD_DIRECTIVE_NOT_EXPECTED, "");
        }
        nextToken();

        Identifier type = eatIdentifier();

        List<Literal> literals = new ArrayList<>();
        for (; ; ) {
            Literal literal = maybeEatLiteral();
            if (literal == null) {
                break;
            }
            literals.add(literal);
        }
        if (directiveMap.containsKey(name.getName())) {
            raiseInternalException(startpos, SpelMessage.DIRECTIVE_NOT_FOUND, name.getName(), type.getName());
            return null;
        }

        int endpos = eatToken(TokenKind.SEMICOLON).endpos;

        Library.DirectiveDef directiveDef = library.findDirectiveDef(type.getName(), name.getName(), literals);
        if (directiveDef == null) {
            raiseInternalException(startpos, SpelMessage.DIRECTIVE_NOT_FOUND, name.getName(), type.getName());
        }
        return new DirectiveStatement(toPos(startpos, endpos), type, name, directiveDef);
    }

    private VariableDeclareStatement tryEatVariableDeclareStatement() throws ParseException {
        if (!moreTokens() || !peekIdentifierToken(Keyword.LET.idt)) {
            return null;
        }

        Token let = eatKeyWord(Keyword.LET);
        Identifier identifier = eatIdentifier();
        ExpressionNode initializer = null;
        if (peekToken(TokenKind.ASSIGN, true)) {
            initializer = eatExpression();
        }
        Token token = eatToken(TokenKind.SEMICOLON);
        return new VariableDeclareStatement(toPos(let.startpos, token.endpos), identifier, initializer);
    }


    private ForeachStatement tryEatForeachStatement() throws ParseException {
        if (!peekIdentifierToken(Keyword.FOR.idt)) {
            return null;
        }
        Token forKW = eatKeyWord(Keyword.FOR);
        eatToken(TokenKind.LPAREN);
        eatKeyWord(Keyword.LET);

        Identifier key = eatIdentifier();
        eatToken(TokenKind.COMMA);
        Identifier val = eatIdentifier();

        eatKeyWord(Keyword.OF);

        ExpressionNode collection = eatExpression();
        eatToken(TokenKind.RPAREN);
        Block block = eatBlock(true);

        return new ForeachStatement(toPos(forKW.startpos, block.getEndPosition()), key, val, collection, block);
    }

    private IfStatement tryEatIfStatement() throws ParseException {
        if (!peekIdentifierToken(Keyword.IF.idt)) {
            return null;
        }

        Token ifKW = eatKeyWord(Keyword.IF);
        eatToken(TokenKind.LPAREN);
        ExpressionNode prediction = eatExpression();
        eatToken(TokenKind.RPAREN);
        Block trueBlock = eatBlock(true);
        int endPos = trueBlock.getEndPosition();
        Statement elseBlock = null;

        if (peekIdentifierToken(Keyword.ELSE.idt)) {
            eatToken(TokenKind.IDENTIFIER);
            if (peekIdentifierToken(Keyword.IF.idt)) { // else if
                //
                elseBlock = tryEatIfStatement();
            } else {
                elseBlock = eatBlock(true);
            }
            assert elseBlock != null;
            endPos = elseBlock.getEndPosition();
        }

        return new IfStatement(toPos(ifKW.startpos, endPos), prediction, trueBlock, elseBlock);
    }

    private Block eatBlock(boolean mustCurly) {

        Token s = mustCurly ? eatToken(TokenKind.LCURLY) : null;
        List<Statement> statements = new ArrayList<>();
        while (moreTokens()) {
            if (s != null && peekToken(TokenKind.RCURLY)) {
                break;
            }
            if (peekToken(TokenKind.SEMICOLON, true)) {
                continue;
            }
            Statement e = eatStatement();
            if (e instanceof DirectiveStatement) {
                DirectiveStatement directiveStatement = (DirectiveStatement) e;
                directiveMap.put(directiveStatement.getName().getName(), directiveStatement);
            } else {
                statements.add(e);
            }
        }
        Token e = s != null ? eatToken(TokenKind.RCURLY) : null;
        int pos;
        if (e != null) {
            pos = toPos(s.startpos, e.endpos);
        } else if (!statements.isEmpty()) {
            pos = toPos(statements.get(0).getStartPosition(), statements.get(statements.size() - 1).getEndPosition());
        } else {
            raiseInternalException(0, SpelMessage.MORE_INPUT);
            // not hit
            return null;
        }

        return new Block(pos, statements);
    }

    private Identifier eatIdentifier() {
        Token token = eatToken(TokenKind.IDENTIFIER);
        if (tryMatchKeyWords(token.data) != null) {
            raiseInternalException(token.startpos, SpelMessage.KEYWORD_DIRECTIVE_NOT_EXPECTED, token.data);
        }
        return new Identifier(toPos(token), token.data);
    }

    //	expression
    //    : logicalOrExpression
    //      ( (ASSIGN^ logicalOrExpression)
    //	    | (DEFAULT^ logicalOrExpression)
    //	    | (QMARK^ expression COLON! expression)
    //      | (ELVIS^ expression))?;
    private ExpressionNode eatExpression() {
        ExpressionNode expr = eatLogicalOrExpression();
        if (moreTokens()) {
            Token t = peekToken();
            if (hasAssign && t.kind == TokenKind.ASSIGN) { // a=b
                nextToken();
                ExpressionNode assignedValue = eatLogicalOrExpression();
                if (expr instanceof MaybeLValue) {
                    MaybeLValue lValue = (MaybeLValue) expr;
                    lValue.markLValue();
                    return new Assign(toPos(t), lValue, assignedValue);
                } else {
                    throw new ParseException("需要 LValue:" + expr.getPos());
                }
            }

            if (t.kind == TokenKind.QMARK) { // a?b:c
                if (expr == null) {
                    expr = Literal.ofNull(toPos(t.startpos - 1, t.endpos - 1));
                }
                nextToken();
                ExpressionNode ifTrueExprValue = eatExpression();
                eatToken(TokenKind.COLON);
                ExpressionNode ifFalseExprValue = eatExpression();
                return new Ternary(toPos(t), expr, ifTrueExprValue, ifFalseExprValue);
            }
        }
        return expr;
    }

    //logicalOrExpression : logicalAndExpression (OR^ logicalAndExpression)*;
    private ExpressionNode eatLogicalOrExpression() {
        ExpressionNode expr = eatLogicalAndExpression();
        while (peekToken(TokenKind.SYMBOLIC_OR)) {
            Token t = nextToken(); //consume OR
            ExpressionNode rhExpr = eatLogicalAndExpression();
            checkOperands(t, expr, rhExpr);
            expr = new LogicRelationalExpression(toPos(t), expr, Operator.OR, rhExpr);
        }
        return expr;
    }

    // logicalAndExpression : relationalExpression (AND^ relationalExpression)*;
    private ExpressionNode eatLogicalAndExpression() {
        ExpressionNode expr = eatRelationalExpression();
        while (peekToken(TokenKind.SYMBOLIC_AND)) {
            Token t = nextToken();// consume 'AND'
            ExpressionNode rhExpr = eatRelationalExpression();
            checkOperands(t, expr, rhExpr);
            expr = new LogicRelationalExpression(toPos(t), expr, Operator.AND, rhExpr);
        }
        return expr;
    }

    // relationalExpression : sumExpression (relationalOperator^ sumExpression)?;
    private ExpressionNode eatRelationalExpression() {
        ExpressionNode expr = eatSumExpression();
        Token relationalOperatorToken = maybeEatRelationalOperator();
        if (relationalOperatorToken != null) {
            Token t = nextToken(); //consume relational operator token
            ExpressionNode rhExpr = eatSumExpression();
            checkOperands(t, expr, rhExpr);
            TokenKind tk = relationalOperatorToken.kind;

            if (relationalOperatorToken.isNumericRelationalOperator()) {
                return new BinaryOperator(toPos(t), expr, Operator.fromToken(t.kind), rhExpr);
            }

            if (tk == TokenKind.TILDE) {
                return new BinaryOperator(toPos(t), expr, Operator.MATCH, rhExpr);
            }
            return new BinaryOperator(toPos(t), expr, Operator.IN, rhExpr);
        }
        return expr;
    }

    //sumExpression: productExpression ( (PLUS^ | MINUS^) productExpression)*;
    private ExpressionNode eatSumExpression() {
        ExpressionNode expr = eatProductExpression();
        while (peekToken(TokenKind.PLUS, TokenKind.MINUS)) {
            Token t = nextToken();//consume PLUS or MINUS
            ExpressionNode rhExpr = eatProductExpression();
            checkRightOperand(t, rhExpr);
            if (t.kind == TokenKind.PLUS || t.kind == TokenKind.MINUS) {
                expr = new BinaryOperator(toPos(t), expr, Operator.fromToken(t.kind), rhExpr);
            }
        }
        return expr;
    }

    // productExpression: powerExpr ((STAR^ | DIV^| MOD^) powerExpr)* ;
    private ExpressionNode eatProductExpression() {
        ExpressionNode expr = eatPowerIncDecExpression();
        while (peekToken(TokenKind.STAR, TokenKind.DIV, TokenKind.MOD)) {
            Token t = nextToken(); // consume STAR/DIV/MOD
            ExpressionNode rhExpr = eatPowerIncDecExpression();
            checkOperands(t, expr, rhExpr);
            expr = new BinaryOperator(toPos(t), expr, Operator.fromToken(t.kind), rhExpr);
        }
        return expr;
    }

    // powerExpr  : unaryExpression (POWER^ unaryExpression)? (INC || DEC) ;
    private ExpressionNode eatPowerIncDecExpression() {
        return eatUnaryExpression();
    }

    // unaryExpression: (PLUS^ | MINUS^ | BANG^ | INC^ | DEC^) unaryExpression | primaryExpression ;
    private ExpressionNode eatUnaryExpression() {
        if (peekToken(TokenKind.PLUS, TokenKind.MINUS, TokenKind.NOT)) {
            Token t = nextToken();
            ExpressionNode expr = eatUnaryExpression();
            return new UnaryOperator(toPos(t), Operator.fromToken(t.kind), expr);
        }

        if (peekIdentifierToken("typeof")) {
            Token t = nextToken();
            ExpressionNode expr = eatUnaryExpression();
            return new UnaryOperator(toPos(t), Operator.TYPEOF, expr);
        }
        return eatPrimaryExpression();
    }

    private FunctionCall maybeEatFuncCall(VariableReference prefix) {
        int tokenIdx = this.tokenStreamPointer;
        int dotSize = 0;
        StringBuilder sb = new StringBuilder(prefix.getName());
        while (peekToken(TokenKind.DOT, true)) {
            if (peekToken(TokenKind.IDENTIFIER)) {
                Token token = nextToken();
                sb.append('.').append(token.data);
                dotSize++;
                if (peekToken(TokenKind.LPAREN)) {
                    ExpressionNode[] args = maybeEatMethodArgs();
                    if (args != null) {
                        String funcName = sb.toString();
                        Library.Function func = library.findFunc(funcName);

                        if (func == null && dotSize == 1 && directiveMap.containsKey(prefix.getName())) {
                            DirectiveStatement directiveStatement = directiveMap.get(prefix.getName());
                            func = directiveStatement.getDirectiveDef().findFunc(prefix.getName(), token.data);
                        }

                        if (func == null) {
                            raiseInternalException(prefix.getStartPosition(), SpelMessage.FUNCTION_NOT_DEFINED, funcName);
                        }
                        return new FunctionCall(func, funcName, toPos(prefix.getStartPosition(), args.length > 0 ? args[args.length - 1].getEndPosition() + 1 : prefix.getStartPosition() + funcName.length() + 2), args);
                    }
                }
            } else {
                break;
            }
        }

        this.tokenStreamPointer = tokenIdx;
        return null;
    }

    // primaryExpression : startNode (node)? -> ^(EXPRESSION startNode (node)?);
    private ExpressionNode eatPrimaryExpression() {
        ExpressionNode start = eatStartNode(); // always a start node

        if ((start instanceof VariableReference) && !((VariableReference) start).isRoot()) {
            FunctionCall functionCall = maybeEatFuncCall((VariableReference) start);
            if (functionCall != null) {
                start = functionCall;
            }
        }

        ExpressionNode current;
        while ((current = maybeEatNode(start)) != null) {
            start = current;
        }
        return start;
    }

    // node : ((DOT dottedNode) | (SAFE_NAVI dottedNode) | nonDottedNode)+;
    private ExpressionNode maybeEatNode(ExpressionNode parent) {
        ExpressionNode expr;
        if (peekToken(TokenKind.DOT)) {
            expr = eatDottedNode(parent);
        } else {
            expr = maybeEatNonDottedNode(parent);
        }

        return expr;
    }

    // nonDottedNode: indexer;
    private ExpressionNode maybeEatNonDottedNode(ExpressionNode parent) {
        if (peekToken(TokenKind.LSQUARE)) {
            return maybeEatIndexer(parent);
        }
        return null;
    }

    private ConstantVal maybeConstant(Token prefix) {
        int tokenIdx = this.tokenStreamPointer;
        if (!peekToken(TokenKind.DOT, true)) {
            return null;
        }
        Token token = peekToken();
        if (token == null || token.kind != TokenKind.IDENTIFIER) {
            this.tokenStreamPointer = tokenIdx;
            return null;
        }

        String key = token.data;
        if ("$".equals(prefix.data)) {
            library.markRootProp(key);
            this.tokenStreamPointer = tokenIdx;
            return null;
        }
        nextToken();
        Library.Constant constant = library.findConstant(prefix.data, key);
        if (constant == null) {
            raiseInternalException(prefix.startpos, SpelMessage.CONSTANT_NOT_FIND, prefix.data, key);
            return null;
        }
        return new ConstantVal(toPos(prefix.startpos, token.endpos), prefix.data + "." + key, constant);
    }

    //dottedNode
    // : ((methodOrProperty
    //	  | functionOrVar
    //    | projection
    //    | selection
    //    | firstSelection
    //    | lastSelection
    //    ))
    //	;
    private ExpressionNode eatDottedNode(ExpressionNode parent) {
        ExpressionNode result;
        Token t = nextToken();// it was a '.' or a '?.'
        if ((result = maybeEatProperty(parent)) != null) {
            return result;
        }
        if (peekToken() == null) {
            // unexpectedly ran out of data
            raiseInternalException(t.startpos, SpelMessage.OOD);
        } else {
            raiseInternalException(t.startpos, SpelMessage.UNEXPECTED_DATA_AFTER_DOT, toString(peekToken()));
        }
        return null;
    }

    // functionOrVar
    // : (POUND ID LPAREN) => function
    // | var
    //
    // function : POUND id=ID methodArgs -> ^(FUNCTIONREF[$id] methodArgs);
    // var : POUND id=ID -> ^(VARIABLEREF[$id]);
    private ExpressionNode maybeEatFunctionOrVar() {
        Token t = eatToken(TokenKind.IDENTIFIER);
        ExpressionNode[] args = maybeEatMethodArgs();
        if (args != null) {
            Library.Function func = library.findFunc(t.data);
            if (func == null) {
                raiseInternalException(t.startpos, SpelMessage.FUNCTION_NOT_DEFINED, t.data);
            }
            return new FunctionCall(func, t.data, toPos(t.startpos, t.endpos), args);

        }
        if (t.data.charAt(0) == '$') {
            ConstantVal constantVal = maybeConstant(t);
            if (constantVal != null) {
                return constantVal;
            }
        }
        return new VariableReference(t.data, toPos(t.startpos, t.endpos));
    }

    // methodArgs : LPAREN! (argument (COMMA! argument)* (COMMA!)?)? RPAREN!;
    private ExpressionNode[] maybeEatMethodArgs() {
        if (!peekToken(TokenKind.LPAREN)) {
            return null;
        }
        List<ExpressionNode> args = new ArrayList<>();
        consumeArguments(args);
        eatToken(TokenKind.RPAREN);
        return args.toArray(new ExpressionNode[args.size()]);
    }

    /**
     * Used for consuming arguments for either a method or a constructor call
     */
    private void consumeArguments(List<ExpressionNode> accumulatedArguments) {
        int pos = peekToken().startpos;
        Token next = null;
        do {
            nextToken();// consume ( (first time through) or comma (subsequent times)
            Token t = peekToken();
            if (t == null) {
                raiseInternalException(pos, SpelMessage.RUN_OUT_OF_ARGUMENTS);
            }
            if (t.kind != TokenKind.RPAREN) {
                ExpressionNode e;
                if (t.kind == TokenKind.EXPAND || t.kind == TokenKind.SAFE_EXPAND) {
                    nextToken();
                    e = new ExpandArrArg(toPos(t), eatExpression());
                } else {
                    e = eatExpression();
                }
                accumulatedArguments.add(e);
            }
            next = peekToken();
        } while (next != null && next.kind == TokenKind.COMMA);

        if (next == null) {
            raiseInternalException(pos, SpelMessage.RUN_OUT_OF_ARGUMENTS);
        }
    }

    //startNode
    // : parenExpr | literal
    //	    | type
    //	    | methodOrProperty
    //	    | functionOrVar
    //	    | projection
    //	    | selection
    //	    | firstSelection
    //	    | lastSelection
    //	    | indexer
    //	    | constructor
    private ExpressionNode eatStartNode() {
        ExpressionNode node;
        if ((node = maybeEatLiteral()) != null) {
            return node;
        } else if ((node = maybeEatInlineList()) != null || (node = maybeEatInlineObject()) != null) {
            return node;
        } else if ((node = maybeEatParenExpression()) != null || (node = maybeEatNullReference()) != null) {
            return node;
        }
        return maybeEatFunctionOrVar();
    }

    private InlineObject maybeEatInlineObject() {
        Token t = peekToken();
        if (!peekToken(TokenKind.LCURLY, true)) {
            return null;
        }
        InlineObject expr;
        Token closingCurly = peekToken();
        if (peekToken(TokenKind.RCURLY, true)) {
            // empty object '{}'
            expr = new InlineObject(toPos(t.startpos, closingCurly.endpos), EMPTY_STR_ARR, EMPTY_NODE_ARR);
        } else {
            Map<Object, ExpressionNode> map = new LinkedHashMap<>();
            do {
                Token k = peekToken();
                if (k == null) {
                    raiseInternalException(t.startpos, SpelMessage.MISSING_INLINE_OBJECT_ELEMENT);
                }
                if (k.kind == TokenKind.EXPAND || k.kind == TokenKind.SAFE_EXPAND) {
                    nextToken();
                    ExpandArrArg expand = new ExpandArrArg(toPos(t), eatExpression());
                    map.put(InlineObject.expandKey(), expand);
                } else {
                    nextToken();
                    String key = null;
                    if (k.kind == TokenKind.LITERAL_STRING) {
                        try {
                            JsonNode node = JsonUtil.MAPPER.readTree(k.data);
                            if (!node.isTextual()) {
                                raiseInternalException(t.startpos, SpelMessage.NOT_SUPPORT_INLINE_OBJECT_KEY, k);
                            }
                            key = node.asText();
                        } catch (ParseException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new ParseException(e.getMessage());
                        }
                    } else if (k.kind == TokenKind.IDENTIFIER) {
                        key = k.data;
                        if (peekToken(TokenKind.COMMA)) {
                            ExpressionNode old = map.put(key, new VariableReference(key, toPos(k)));
                            if (old != null) {
                                raiseInternalException(t.startpos, SpelMessage.INLINE_OBJECT_DUPLICATE_KEY, k);
                            }
                            continue;
                        }
                    } else if (k.kind == TokenKind.RCURLY) {
                        break;
                    } else {
                        raiseInternalException(t.startpos, SpelMessage.NOT_SUPPORT_INLINE_OBJECT_KEY, k);
                    }
                    eatToken(TokenKind.COLON);
                    ExpressionNode old = map.put(key, eatExpression());
                    if (old != null) {
                        raiseInternalException(t.startpos, SpelMessage.INLINE_OBJECT_DUPLICATE_KEY, k);
                    }
                }
            } while (peekToken(TokenKind.COMMA, true));
            closingCurly = eatToken(TokenKind.RCURLY);

            Object[] keys = new Object[map.size()];
            keys = map.keySet().toArray(keys);
            ExpressionNode[] values = new ExpressionNode[map.size()];
            values = map.values().toArray(values);
            expr = new InlineObject(toPos(t.startpos, closingCurly.endpos), keys, values);
        }
        return expr;
    }

    private Literal maybeEatNullReference() {
        if (peekToken(TokenKind.IDENTIFIER)) {
            Token nullToken = peekToken();
            if (!nullToken.stringValue().equals("null")) {
                return null;
            }
            nextToken();
            return Literal.ofNull(toPos(nullToken));
        }
        return null;
    }

    // list = LCURLY (element (COMMA element)*) RCURLY
    private InlineList maybeEatInlineList() {
        Token t = peekToken();
        if (!peekToken(TokenKind.LSQUARE, true)) {
            return null;
        }
        InlineList expr;
        Token closingCurly = peekToken();
        if (peekToken(TokenKind.RSQUARE, true)) {
            // empty list '[]'
            expr = new InlineList(toPos(t.startpos, closingCurly.endpos));
        } else {
            List<ExpressionNode> listElements = new ArrayList<>();
            do {
                ExpressionNode e;
                Token ct = peekToken();
                if (ct == null) {
                    // unexpectedly ran out of data
                    raiseInternalException(t.startpos, SpelMessage.OOD);
                }
                if (ct.kind == TokenKind.SAFE_EXPAND || ct.kind == TokenKind.EXPAND) {
                    nextToken();
                    e = new ExpandArrArg(toPos(ct), eatExpression());
                } else {
                    e = eatExpression();
                }
                listElements.add(e);
            } while (peekToken(TokenKind.COMMA, true));

            closingCurly = eatToken(TokenKind.RSQUARE);
            expr = new InlineList(toPos(t.startpos, closingCurly.endpos), listElements.toArray(new ExpressionNode[listElements.size()]));
        }
        return expr;
    }

    private Indexer maybeEatIndexer(ExpressionNode parent) {
        Token t = peekToken();
        if (!peekToken(TokenKind.LSQUARE, true)) {
            return null;
        }
        ExpressionNode expr = eatExpression();
        eatToken(TokenKind.RSQUARE);
        return new Indexer(toPos(t), parent, expr);
    }

    private boolean isValidQualifiedId(Token node) {
        if (node == null || node.kind == TokenKind.LITERAL_STRING) {
            return false;
        }
        if (node.kind == TokenKind.DOT || node.kind == TokenKind.IDENTIFIER) {
            return true;
        }
        String value = node.stringValue();
        return StringUtils.hasLength(value) && VALID_QUALIFIED_ID_PATTERN.matcher(value).matches();
    }

    // This is complicated due to the support for dollars in identifiers.  Dollars are normally separate tokens but
    // there we want to combine a series of identifiers and dollars into a single identifier
    private PropertyReference maybeEatProperty(ExpressionNode parent) {
        if (peekToken(TokenKind.IDENTIFIER)) {
            Token methodOrPropertyName = nextToken();
            // property
            return new PropertyReference(methodOrPropertyName.data, toPos(methodOrPropertyName), parent);
        }
        return null;
    }

    //	literal
    //  : INTEGER_LITERAL
    //	| boolLiteral
    //	| STRING_LITERAL
    //  | HEXADECIMAL_INTEGER_LITERAL
    //  | REAL_LITERAL
    //	| DQ_STRING_LITERAL
    //	| NULL_LITERAL
    private Literal maybeEatLiteral() {
        Token t = peekToken();
        if (t == null) {
            return null;
        }
        Literal result;
        if (t.kind == TokenKind.LITERAL_INT) {
            result = (Literal.getIntLiteral(t.data, toPos(t), 10));
        } else if (t.kind == TokenKind.LITERAL_LONG) {
            result = (Literal.getLongLiteral(t.data, toPos(t), 10));
        } else if (t.kind == TokenKind.LITERAL_HEXINT) {
            result = (Literal.getIntLiteral(t.data, toPos(t), 16));
        } else if (t.kind == TokenKind.LITERAL_HEXLONG) {
            result = (Literal.getLongLiteral(t.data, toPos(t), 16));
        } else if (t.kind == TokenKind.LITERAL_REAL) {
            result = (Literal.getRealLiteral(t.data, toPos(t), false));
        } else if (t.kind == TokenKind.LITERAL_REAL_FLOAT) {
            result = (Literal.getRealLiteral(t.data, toPos(t), true));
        } else if (peekIdentifierToken("true")) {
            result = (Literal.ofBoolean(toPos(t), true));
        } else if (peekIdentifierToken("false")) {
            result = (Literal.ofBoolean(toPos(t), false));
        } else if (t.kind == TokenKind.LITERAL_STRING) {
            result = (Literal.ofString(toPos(t), t.data));
        } else {
            return null;
        }
        nextToken();
        return result;
    }

    //parenExpr : LPAREN! expression RPAREN!;
    private ExpressionNode maybeEatParenExpression() {
        if (peekToken(TokenKind.LPAREN)) {
            nextToken();
            ExpressionNode expr = eatExpression();
            eatToken(TokenKind.RPAREN);
            return expr;
        } else {
            return null;
        }
    }

    // relationalOperator
    // : EQUAL | NOT_EQUAL | LESS_THAN | LESS_THAN_OR_EQUAL | GREATER_THAN
    // | GREATER_THAN_OR_EQUAL | INSTANCEOF | BETWEEN | MATCHES
    private Token maybeEatRelationalOperator() {
        Token t = peekToken();
        if (t == null) {
            return null;
        }
        if (t.isNumericRelationalOperator()) {
            return t;
        }
        if (t.kind == TokenKind.TILDE) {
            return t;
        }
        return null;
    }

    private Token eatToken(TokenKind expectedKind) {
        Token t = nextToken();
        if (t == null) {
            raiseInternalException(-1, SpelMessage.OOD);
        }
        if (t.kind != expectedKind) {
            raiseInternalException(t.startpos, SpelMessage.NOT_EXPECTED_TOKEN, expectedKind.toString().toLowerCase(), t.getKind().toString().toLowerCase());
        }
        return t;
    }

    private boolean peekToken(TokenKind desiredTokenKind) {
        return peekToken(desiredTokenKind, false);
    }

    private boolean peekToken(TokenKind desiredTokenKind, boolean consumeIfMatched) {
        if (!moreTokens()) {
            return false;
        }
        Token t = peekToken();
        if (t.kind == desiredTokenKind) {
            if (consumeIfMatched) {
                this.tokenStreamPointer++;
            }
            return true;
        }

        if (desiredTokenKind == TokenKind.IDENTIFIER) {
            // might be one of the textual forms of the operators (e.g. NE for != ) - in which case we can treat it as an identifier
            // The list is represented here: Tokenizer.alternativeOperatorNames and those ones are in order in the TokenKind enum
            if (t.kind.ordinal() >= TokenKind.DIV.ordinal() && t.kind.ordinal() <= TokenKind.NOT.ordinal() && t.data != null) {
                // if t.data were null, we'd know it wasn't the textual form, it was the symbol form
                return true;
            }
        }
        return false;
    }

    private boolean peekToken(TokenKind possible1, TokenKind possible2) {
        if (!moreTokens()) {
            return false;
        }
        Token t = peekToken();
        return (t.kind == possible1 || t.kind == possible2);
    }

    private boolean peekToken(TokenKind possible1, TokenKind possible2, TokenKind possible3) {
        if (!moreTokens()) {
            return false;
        }
        Token t = peekToken();
        return t.kind == possible1 || t.kind == possible2 || t.kind == possible3;
    }

    private boolean peekIdentifierToken(String identifierString) {
        if (!moreTokens()) {
            return false;
        }
        Token t = peekToken();
        return t.kind == TokenKind.IDENTIFIER && t.stringValue().equals(identifierString);
    }

    private Token eatKeyWord(Keyword keyword) {
        Token t = eatToken(TokenKind.IDENTIFIER);

        if (!t.stringValue().equals(keyword.idt)) {
            raiseInternalException(t.startpos, SpelMessage.KEYWORD_NOT_MATCH, keyword.idt, t.stringValue());
        }
        return t;
    }

    private boolean moreTokens() {
        return this.tokenStreamPointer < this.tokenStream.size();
    }

    private Token nextToken() {
        if (this.tokenStreamPointer >= this.tokenStreamLength) {
            return null;
        }
        return this.tokenStream.get(this.tokenStreamPointer++);
    }

    private Token peekToken() {
        if (this.tokenStreamPointer >= this.tokenStreamLength) {
            return null;
        }
        return this.tokenStream.get(this.tokenStreamPointer);
    }

    private void raiseInternalException(int pos, SpelMessage message, Object... inserts) {
        throw new ParseException((String) null, pos, message, inserts);
    }

    public String toString(Token t) {
        if (t.getKind().hasPayload()) {
            return t.stringValue();
        }
        return t.kind.toString().toLowerCase();
    }

    private void checkOperands(Token token, ExpressionNode left, ExpressionNode right) {
        checkLeftOperand(token, left);
        checkRightOperand(token, right);
    }

    private void checkLeftOperand(Token token, ExpressionNode operandExpression) {
        if (operandExpression == null) {
            raiseInternalException(token.startpos, SpelMessage.LEFT_OPERAND_PROBLEM);
        }
    }

    private void checkRightOperand(Token token, ExpressionNode operandExpression) {
        if (operandExpression == null) {
            raiseInternalException(token.startpos, SpelMessage.RIGHT_OPERAND_PROBLEM);
        }
    }

    /**
     * Compress the start and end of a token into a single int.
     */
    private int toPos(Token t) {
        return (t.startpos << 16) + t.endpos;
    }

    private int toPos(int start, int end) {
        return (start << 16) + end;
    }

}
