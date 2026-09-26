package org.app;

public class Parser {

    private final String text;
    private int pos = 0;

    public Parser(String text) {
        this.text = text;
    }

    // --- Вспомогательные методы ---

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        skipWhitespace();
        if (pos >= text.length()) return '\0';
        return text.charAt(pos);
    }

    private char getChar() {
        skipWhitespace();
        if (pos >= text.length()) return '\0';
        return text.charAt(pos++);
    }

    private boolean isBinOperation(char ch) {
        return ch == '+' || ch == '-' || ch == '*' || ch == '/';
    }

    // --- Правило: выраж := [+-] операнд {бин. операция операнд} ---

    public void parseExpr() {
        // [+-]
        char next = peek();
        if (next == '+' || next == '-') {
            getChar(); // пропуск унарного знака
        }

        // операнд
        parseOperand();

        // {бин. операция операнд}
        while (isBinOperation(peek())) {
            getChar(); // пропуск бинарной операции
            parseOperand();
        }
    }

    // --- Правило: операнд := имя | константа | "(" выраж ")" ---

    public void parseOperand() {
        char ch = peek();

        if (ch == '\0') {
            throw new RuntimeException("Ошибка: ожидался операнд в конце строки (позиция " + pos + ")");
        }

        // "(" выраж ")"
        if (ch == '(') {
            getChar(); // съедаем '('
            parseExpr();
            if (peek() != ')') {
                throw new RuntimeException("Ошибка: пропущена закрывающая скобка ')' на позиции " + pos);
            }
            getChar(); // съедаем ')'
            return;
        }

        // константа
        if (Character.isDigit(ch)) {
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            return;
        }

        // имя
        if (Character.isLetter(ch) || ch == '_') {
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
                pos++;
            }
            return;
        }

        throw new RuntimeException("Ошибка: недопустимый символ операнда '" + ch + "' на позиции " + pos);
    }

    // Главный метод синтаксического анализа
    public boolean parse() {
        parseExpr();
        skipWhitespace();
        if (pos < text.length()) {
            throw new RuntimeException("Ошибка: лишние символы после выражения на позиции " + pos + " ('" + text.substring(pos) + "')");
        }
        return true;
    }

    // --- Пример использования ---

    public static void main(String[] args) {
        String[] tests = {
                "(x + y) * z"
        };

        for (String test : tests) {
            try {
                Parser parser = new Parser(test);
                parser.parse();
                System.out.println("✅ Выражение корректно: " + test);
            } catch (Exception e) {
                System.out.println("❌ " + e.getMessage() + " | Выражение: " + test);
            }
        }
    }
}