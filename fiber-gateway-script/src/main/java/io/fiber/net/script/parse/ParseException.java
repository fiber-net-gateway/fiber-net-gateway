package io.fiber.net.script.parse;

public class ParseException extends RuntimeException {
    protected String expressionString;
    protected int position; // -1 if not known - but should be known in all reasonable cases

    /**
     * Creates a new expression exception.
     *
     * @param expressionString the expression string
     * @param message          a descriptive message
     */
    public ParseException(String expressionString, String message) {
        super(message);
        this.position = -1;
        this.expressionString = expressionString;
    }

    /**
     * Creates a new expression exception.
     *
     * @param expressionString the expression string
     * @param position         the position in the expression string where the problem occurred
     * @param message          a descriptive message
     */
    public ParseException(String expressionString, int position, String message) {
        super(message);
        this.position = position;
        this.expressionString = expressionString;
    }

    /**
     * Creates a new expression exception.
     *
     * @param position the position in the expression string where the problem occurred
     * @param message  a descriptive message
     */
    public ParseException(int position, String message) {
        super(message);
        this.position = position;
    }

    /**
     * Creates a new expression exception.
     *
     * @param position the position in the expression string where the problem occurred
     * @param message  a descriptive message
     * @param cause    the underlying cause of this exception
     */
    public ParseException(int position, String message, Throwable cause) {
        super(message, cause);
        this.position = position;
    }

    public ParseException(int position, SpelMessage spelMessage, Object... addition) {
        super(spelMessage.formatMessage(position, addition));
        this.position = position;
    }

    public ParseException(Throwable cause, int position, SpelMessage spelMessage, Object... addition) {
        super(spelMessage.formatMessage(position, addition), cause);
        this.position = position;
    }

    /**
     * Creates a new expression exception.
     */
    public ParseException(String expressionString, int position, SpelMessage spelMessage, Object... addition) {
        super(toDetailedString(expressionString, position, spelMessage, addition));
        this.expressionString = expressionString;
        this.position = position;
    }

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }

    private static String toDetailedString(String expressionString, int position, SpelMessage spelMessage, Object... addition) {
        StringBuilder output = new StringBuilder();
        if (expressionString != null) {
            output.append("Expression '");
            output.append(expressionString);
            output.append("'");
            if (position != -1) {
                output.append(" @ ");
                output.append(position);
            }
            output.append(": ");
        }
        output.append(spelMessage.formatMessagePos(position, addition));
        return output.toString();
    }

    public final String getExpressionString() {
        return this.expressionString;
    }

    public final int getPosition() {
        return position;
    }
}
