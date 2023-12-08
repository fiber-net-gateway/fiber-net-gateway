package io.fiber.net.script.parse;


public interface Node {

    <T> T accept(NodeVisitor<T> nodeVisitor);

    void toStringAST(StringBuilder sb);


    /**
     * @return the start position of this Ast node in the expression string
     */
    int getStartPosition();

    /**
     * @return the end position of this Ast node in the expression string
     */
    int getEndPosition();


}
