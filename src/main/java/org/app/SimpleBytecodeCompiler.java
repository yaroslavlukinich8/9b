package org.app;

import java.util.*;

public class SimpleBytecodeCompiler {

    public enum Op {
        PUSH, LOAD, STORE,
        ADD, SUB, MUL, DIV,
        POW, FACT,                  // ← новые
        LT, GT, LE, GE, EQ, NE,
        DUP, POP,
        JMP_TRUE_KEEP, JMP_FALSE_KEEP,
        PRINT, JMP, JMP_FALSE, HALT
    }

    public static class Instruction {
        public Op op;
        public int arg;

        public Instruction(Op op, int arg) {
            this.op = op;
            this.arg = arg;
        }

        @Override
        public String toString() {
            return arg != -1 ? String.format("%-15s %d", op, arg) : op.toString();
        }
    }

    private final String src;
    private int pos = 0;
    private final List<Instruction> code = new ArrayList<>();
    private final Map<String, Integer> varIndices = new HashMap<>();
    private int nextVarIndex = 0;

    public SimpleBytecodeCompiler(String src) {
        this.src = src;
    }

    public List<Instruction> compile() {
        skipWhitespace();
        while (pos < src.length()) {
            parseStatement();
            skipWhitespace();
        }
        code.add(new Instruction(Op.HALT, -1));
        return code;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private void parseStatement() {
        skipWhitespace();
        if (pos >= src.length()) return;

        if (startsWith("int")) {
            pos += 3;
            do {
                skipWhitespace();
                String name = parseIdentifier();
                int varIndex = nextVarIndex++;
                varIndices.put(name, varIndex);

                skipWhitespace();
                if (peek() == '=') {
                    pos++;
                    parseExpression();
                } else {
                    code.add(new Instruction(Op.PUSH, 0));
                }
                code.add(new Instruction(Op.STORE, varIndex));
                skipWhitespace();
            } while (match(','));
            expect(';');
        }
        else if (startsWith("print")) {
            pos += 5;
            parseExpression();
            code.add(new Instruction(Op.PRINT, -1));
            expect(';');
        }
        else if (startsWith("if")) {
            pos += 2;
            parseExpression();

            int jumpFalseIndex = code.size();
            code.add(new Instruction(Op.JMP_FALSE, -1));

            executeBlockContent();
            skipWhitespace();

            if (startsWith("else")) {
                pos += 4;
                int jumpElseIndex = code.size();
                code.add(new Instruction(Op.JMP, -1));

                code.get(jumpFalseIndex).arg = code.size();
                executeBlockContent();
                code.get(jumpElseIndex).arg = code.size();
            } else {
                code.get(jumpFalseIndex).arg = code.size();
            }
        }
        else if (startsWith("while")) {
            pos += 5;
            int loopStart = code.size();

            parseExpression();

            int jumpExitIndex = code.size();
            code.add(new Instruction(Op.JMP_FALSE, -1));

            executeBlockContent();
            code.add(new Instruction(Op.JMP, loopStart));

            code.get(jumpExitIndex).arg = code.size();
        }
        else {
            String name = parseIdentifier();
            Integer varIndex = varIndices.get(name);
            if (varIndex == null) {
                throw new RuntimeException("Неизвестная переменная: " + name);
            }
            skipWhitespace();
            expect('=');
            parseExpression();
            code.add(new Instruction(Op.STORE, varIndex));
            expect(';');
        }
    }

    private void executeBlockContent() {
        skipWhitespace();
        if (peek() == '{') {
            pos++;
        }
        skipWhitespace();
        while (pos < src.length() && peek() != '}') {
            parseStatement();
            skipWhitespace();
        }
        if (peek() == '}') {
            pos++;
        }
    }

    // --- Иерархия разбора выражений ---

    private void parseExpression() {                       // ||
        parseLogicalAnd();
        skipWhitespace();
        while (startsWith("||")) {
            pos += 2;
            code.add(new Instruction(Op.DUP, -1));
            int jumpTrue = code.size();
            code.add(new Instruction(Op.JMP_TRUE_KEEP, -1));
            code.add(new Instruction(Op.POP, -1));
            parseLogicalAnd();
            code.get(jumpTrue).arg = code.size();
            skipWhitespace();
        }
    }

    private void parseLogicalAnd() {                       // &&
        parseEquality();
        skipWhitespace();
        while (startsWith("&&")) {
            pos += 2;
            code.add(new Instruction(Op.DUP, -1));
            int jumpFalse = code.size();
            code.add(new Instruction(Op.JMP_FALSE_KEEP, -1));
            code.add(new Instruction(Op.POP, -1));
            parseEquality();
            code.get(jumpFalse).arg = code.size();
            skipWhitespace();
        }
    }

    private void parseEquality() {                         // ==, !=, <>
        parseRelational();
        skipWhitespace();
        while (true) {
            if (startsWith("==")) {
                pos += 2;
                parseRelational();
                code.add(new Instruction(Op.EQ, -1));
            } else if (startsWith("!=")) {
                pos += 2;
                parseRelational();
                code.add(new Instruction(Op.NE, -1));
            } else if (startsWith("<>")) {
                pos += 2;
                parseRelational();
                code.add(new Instruction(Op.NE, -1));
            } else {
                break;
            }
            skipWhitespace();
        }
    }

    private void parseRelational() {                       // <, >, <=, >=
        parseAdditive();
        skipWhitespace();
        while (true) {
            if (startsWith("<=")) {
                pos += 2;
                parseAdditive();
                code.add(new Instruction(Op.LE, -1));
            } else if (startsWith(">=")) {
                pos += 2;
                parseAdditive();
                code.add(new Instruction(Op.GE, -1));
            } else if (startsWith("==")) {
                break;
            } else if (startsWith("!=") || startsWith("<>")) {
                break;
            } else if (match('<')) {
                parseAdditive();
                code.add(new Instruction(Op.LT, -1));
            } else if (match('>')) {
                parseAdditive();
                code.add(new Instruction(Op.GT, -1));
            } else {
                break;
            }
            skipWhitespace();
        }
    }

    private void parseAdditive() {                         // +, -
        parseMultiplicative();
        skipWhitespace();
        while (true) {
            if (match('+')) {
                parseMultiplicative();
                code.add(new Instruction(Op.ADD, -1));
            } else if (match('-')) {
                parseMultiplicative();
                code.add(new Instruction(Op.SUB, -1));
            } else {
                break;
            }
            skipWhitespace();
        }
    }

    private void parseMultiplicative() {                   // *, /
        parsePower();                                      // ← было parseTerm()
        skipWhitespace();
        while (true) {
            if (startsWith("**")) break;                   // ** — не наш уровень
            if (match('*')) {
                parsePower();
                code.add(new Instruction(Op.MUL, -1));
            } else if (match('/')) {
                parsePower();
                code.add(new Instruction(Op.DIV, -1));
            } else {
                break;
            }
            skipWhitespace();
        }
    }

    // Новый уровень: ** — правоассоциативный
    private void parsePower() {
        parseFactorial();
        skipWhitespace();
        if (startsWith("**")) {
            pos += 2;
            parsePower();                                  // рекурсия → правоассоциативность
            code.add(new Instruction(Op.POW, -1));
        }
    }

    // Новый уровень: постфиксный !
    private void parseFactorial() {
        parseTerm();
        skipWhitespace();
        while (pos < src.length() && src.charAt(pos) == '!') {
            // не путать с !=
            if (pos + 1 < src.length() && src.charAt(pos + 1) == '=') break;
            pos++;
            code.add(new Instruction(Op.FACT, -1));
            skipWhitespace();
        }
    }

    private void parseTerm() {                             // числа, переменные, ( )
        skipWhitespace();
        if (match('(')) {
            parseExpression();
            expect(')');
            return;
        }

        char c = peek();
        if (Character.isDigit(c)) {
            int val = 0;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                val = val * 10 + (src.charAt(pos) - '0');
                pos++;
            }
            code.add(new Instruction(Op.PUSH, val));
        } else if (Character.isLetter(c)) {
            String name = parseIdentifier();
            Integer varIndex = varIndices.get(name);
            if (varIndex == null) {
                throw new RuntimeException("Переменная не найдена: " + name);
            }
            code.add(new Instruction(Op.LOAD, varIndex));
        } else {
            throw new RuntimeException("Неожиданный символ в выражении: '" + c +
                    "' на позиции " + pos);
        }
    }

    private String parseIdentifier() {
        skipWhitespace();
        if (pos >= src.length() ||
                !(Character.isLetter(src.charAt(pos)) || src.charAt(pos) == '_')) {
            throw new RuntimeException("Ожидался идентификатор на позиции " + pos +
                    ", но найден: '" + peek() + "'");
        }
        int start = pos;
        while (pos < src.length() &&
                (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        return src.substring(start, pos);
    }

    private boolean startsWith(String prefix) {
        skipWhitespace();
        return src.startsWith(prefix, pos);
    }

    private char peek() {
        skipWhitespace();
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private boolean match(char expected) {
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != expected) {
            throw new RuntimeException("Ожидался символ: " + expected + ", но найден: " + peek());
        }
        pos++;
    }

    // --- Виртуальная Машина ---
    public static class VirtualMachine {
        private final List<Instruction> code;
        private final int[] memory = new int[256];
        private final Deque<Integer> stack = new ArrayDeque<>();
        private int ip = 0;

        public VirtualMachine(List<Instruction> code) {
            this.code = code;
        }

        public void run() {
            while (ip < code.size()) {
                Instruction ins = code.get(ip++);
                switch (ins.op) {
                    case PUSH: stack.push(ins.arg); break;
                    case LOAD: stack.push(memory[ins.arg]); break;
                    case STORE: memory[ins.arg] = stack.pop(); break;
                    case ADD: { int b = stack.pop(), a = stack.pop(); stack.push(a + b); break; }
                    case SUB: { int b = stack.pop(), a = stack.pop(); stack.push(a - b); break; }
                    case MUL: { int b = stack.pop(), a = stack.pop(); stack.push(a * b); break; }
                    case DIV: {
                        int b = stack.pop(), a = stack.pop();
                        if (b == 0) throw new RuntimeException("Деление на ноль");
                        stack.push(a / b);
                        break;
                    }
                    case POW: {
                        int b = stack.pop(), a = stack.pop();
                        if (b < 0) throw new RuntimeException("Отрицательная степень: " + b);
                        int r = 1;
                        for (int i = 0; i < b; i++) r *= a;
                        stack.push(r);
                        break;
                    }
                    case FACT: {
                        int n = stack.pop();
                        if (n < 0) throw new RuntimeException("Факториал отрицательного числа: " + n);
                        int r = 1;
                        for (int i = 2; i <= n; i++) r *= i;
                        stack.push(r);
                        break;
                    }
                    case LT: { int b = stack.pop(), a = stack.pop(); stack.push(a < b ? 1 : 0); break; }
                    case GT: { int b = stack.pop(), a = stack.pop(); stack.push(a > b ? 1 : 0); break; }
                    case LE: { int b = stack.pop(), a = stack.pop(); stack.push(a <= b ? 1 : 0); break; }
                    case GE: { int b = stack.pop(), a = stack.pop(); stack.push(a >= b ? 1 : 0); break; }
                    case EQ: { int b = stack.pop(), a = stack.pop(); stack.push(a == b ? 1 : 0); break; }
                    case NE: { int b = stack.pop(), a = stack.pop(); stack.push(a != b ? 1 : 0); break; }
                    case DUP: stack.push(stack.peek()); break;
                    case POP: stack.pop(); break;
                    case JMP_TRUE_KEEP: if (stack.peek() != 0) ip = ins.arg; break;
                    case JMP_FALSE_KEEP: if (stack.peek() == 0) ip = ins.arg; break;
                    case PRINT: System.out.println(stack.pop()); break;
                    case JMP: ip = ins.arg; break;
                    case JMP_FALSE: if (stack.pop() == 0) ip = ins.arg; break;
                    case HALT: return;
                }
            }
        }
    }

    public static void main(String[] args) {
        String codeText =
                "int a = 2 ** 10;\n" +
                        "int b = 5!;\n" +
                        "int c = 2 ** 3 ** 2;\n" +
                        "int d = (2 + 3)!;\n" +
                        "int e = 2 * 3! + 1;\n" +
                        "print a; print b; print c; print d; print e;";

        SimpleBytecodeCompiler compiler = new SimpleBytecodeCompiler(codeText);
        List<Instruction> bytecode = compiler.compile();

        System.out.println("--- Сгенерированный байт-код ---");
        for (int i = 0; i < bytecode.size(); i++) {
            System.out.printf("[%2d] %s\n", i, bytecode.get(i));
        }

        System.out.println("\n--- Результат выполнения ---");
        VirtualMachine vm = new VirtualMachine(bytecode);
        vm.run();
    }
}