package org.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleBytecodeCompilerControlFlowTest {

    private ByteArrayOutputStream outBuffer;
    private PrintStream originalOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        outBuffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBuffer));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    /** Компилирует и исполняет программу, возвращает то, что напечатала VM (по строкам). */
    private List<String> run(String src) {
        SimpleBytecodeCompiler compiler = new SimpleBytecodeCompiler(src);
        List<SimpleBytecodeCompiler.Instruction> code = compiler.compile();
        new SimpleBytecodeCompiler.VirtualMachine(code).run();
        return outBuffer.toString().lines().toList();
    }

    /** Только компилирует, без исполнения. */
    private List<SimpleBytecodeCompiler.Instruction> compile(String src) {
        return new SimpleBytecodeCompiler(src).compile();
    }

    /** Индекс первой инструкции с данным opcode (или -1). */
    private int indexOf(List<SimpleBytecodeCompiler.Instruction> code,
                        SimpleBytecodeCompiler.Op op) {
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i).op == op) return i;
        }
        return -1;
    }

    // ==================================================================
    // IF
    // ==================================================================

    @Nested
    @DisplayName("if без else")
    class IfWithoutElse {

        @Test
        @DisplayName("Условие истинно — блок выполняется")
        void conditionTrue() {
            assertEquals(List.of("10"), run("""
                    int x = 5;
                    if x > 0 { x = 10; }
                    print x;
                    """));
        }

        @Test
        @DisplayName("Условие ложно — блок пропускается")
        void conditionFalse() {
            assertEquals(List.of("5"), run("""
                    int x = 5;
                    if x > 100 { x = 10; }
                    print x;
                    """));
        }

        @Test
        @DisplayName("Генерируется JMP_FALSE, адрес указывает за блок")
        void generatesJumpFalse() {
            List<SimpleBytecodeCompiler.Instruction> code = compile("""
                    int x = 1;
                    if x > 0 { x = 2; }
                    """);
            int jmpIdx = indexOf(code, SimpleBytecodeCompiler.Op.JMP_FALSE);
            assertTrue(jmpIdx >= 0, "Должна быть инструкция JMP_FALSE");

            int target = code.get(jmpIdx).arg;
            assertTrue(target > jmpIdx, "Адрес перехода должен быть вперёд");
            assertTrue(target <= code.size(), "Адрес не должен выходить за код");
        }
    }

    @Nested
    @DisplayName("if / else")
    class IfElse {

        @Test
        @DisplayName("Истинная ветка")
        void thenBranch() {
            assertEquals(List.of("1"), run("""
                    int x = 1;
                    if x > 0 { print 1; } else { print 2; }
                    """));
        }

        @Test
        @DisplayName("Ложная ветка")
        void elseBranch() {
            assertEquals(List.of("2"), run("""
                    int x = 1;
                    if x < 0 { print 1; } else { print 2; }
                    """));
        }

        @Test
        @DisplayName("Генерируются JMP_FALSE и JMP, адреса корректны")
        void generatesBothJumps() {
            List<SimpleBytecodeCompiler.Instruction> code = compile("""
                    int x = 1;
                    if x > 0 { x = 2; } else { x = 3; }
                    """);
            int jmpFalseIdx = indexOf(code, SimpleBytecodeCompiler.Op.JMP_FALSE);
            int jmpIdx = indexOf(code, SimpleBytecodeCompiler.Op.JMP);
            assertTrue(jmpFalseIdx >= 0);
            assertTrue(jmpIdx > jmpFalseIdx, "JMP идёт после JMP_FALSE");

            int elseStart = code.get(jmpFalseIdx).arg;
            int afterElse = code.get(jmpIdx).arg;

            assertEquals(jmpIdx + 1, elseStart,
                    "JMP_FALSE должен вести в начало else-ветки (сразу за JMP)");
            assertTrue(afterElse > jmpIdx, "JMP должен вести за else-ветку");
            assertTrue(afterElse <= code.size(), "Адрес не должен выходить за код");
        }
    }

    // ==================================================================
    // WHILE
    // ==================================================================

    @Nested
    @DisplayName("while")
    class WhileLoop {

        @Test
        @DisplayName("Цикл с предусловием — ноль итераций")
        void zeroIterations() {
            assertEquals(List.of("5"), run("""
                    int x = 5;
                    while x > 100 { x = x - 1; }
                    print x;
                    """));
        }

        @Test
        @DisplayName("Одна итерация")
        void oneIteration() {
            assertEquals(List.of("4"), run("""
                    int x = 5;
                    while x > 4 { x = x - 1; }
                    print x;
                    """));
        }

        @Test
        @DisplayName("Несколько итераций — счётчик уменьшается до нуля")
        void manyIterations() {
            assertEquals(List.of("0"), run("""
                    int x = 5;
                    while x > 0 { x = x - 1; }
                    print x;
                    """));
        }

        @Test
        @DisplayName("Накопление суммы в цикле: 1+2+3+4+5 = 15")
        void accumulate() {
            assertEquals(List.of("15"), run("""
                    int i = 1, sum = 0;
                    while i <= 5 { sum = sum + i; i = i + 1; }
                    print sum;
                    """));
        }

        @Test
        @DisplayName("Тело цикла выполняется ровно N раз (проверка через побочный эффект)")
        void bodyExecutedExactlyNTimes() {
            assertEquals(List.of("5", "4", "3", "2", "1"), run("""
                    int i = 5;
                    while i > 0 { print i; i = i - 1; }
                    """));
        }

        @Test
        @DisplayName("Обратный переход JMP указывает на начало условия")
        void jumpBackToCondition() {
            List<SimpleBytecodeCompiler.Instruction> code = compile("""
                    int x = 3;
                    while x > 0 { x = x - 1; }
                    """);

            int jmpFalseIdx = indexOf(code, SimpleBytecodeCompiler.Op.JMP_FALSE);
            assertTrue(jmpFalseIdx >= 0, "Должен быть JMP_FALSE для выхода");

            int backwardJmpIdx = -1;
            for (int i = jmpFalseIdx + 1; i < code.size(); i++) {
                if (code.get(i).op == SimpleBytecodeCompiler.Op.JMP
                        && code.get(i).arg <= jmpFalseIdx) {
                    backwardJmpIdx = i;
                    break;
                }
            }
            assertTrue(backwardJmpIdx >= 0,
                    "Должен быть обратный JMP на начало условия");
            assertTrue(code.get(backwardJmpIdx).arg < backwardJmpIdx,
                    "Адрес обратного JMP должен быть меньше его позиции");
        }
    }

    // ==================================================================
    // Вложенные конструкции
    // ==================================================================

    @Nested
    @DisplayName("Вложенные if/while")
    class NestedControlFlow {

        @Test
        @DisplayName("if внутри while")
        void ifInsideWhile() {
            assertEquals(List.of("3"), run("""
                    int i = 1, count = 0;
                    while i <= 6 {
                        if i == 2 { count = count + 1; }
                        if i == 4 { count = count + 1; }
                        if i == 6 { count = count + 1; }
                        i = i + 1;
                    }
                    print count;
                    """));
        }

        @Test
        @DisplayName("while внутри if")
        void whileInsideIf() {
            assertEquals(List.of("6"), run("""
                    int x = 1, sum = 0;
                    if x > 0 {
                        int i = 1;
                        while i <= 3 { sum = sum + i; i = i + 1; }
                    }
                    print sum;
                    """));
        }

        @Test
        @DisplayName("Вложенные while")
        void nestedWhile() {
            assertEquals(List.of("12"), run("""
                    int i = 1, sum = 0;
                    while i <= 2 {
                        int j = 1;
                        while j <= 3 { sum = sum + j; j = j + 1; }
                        i = i + 1;
                    }
                    print sum;
                    """));
        }
    }

    // ==================================================================
    // Логические условия (&&, ||, сравнения)
    // ==================================================================

    @Nested
    @DisplayName("Условия с логикой")
    class LogicalConditions {

        @ParameterizedTest(name = "x={0}, y={1} → ветка {2}")
        @CsvSource({
                "1, 1, 1",
                "1, 0, 2",
                "0, 1, 2",
                "0, 0, 2"
        })
        @DisplayName("if с &&")
        void ifWithAnd(int x, int y, int expected) {
            String src = String.format("""
                    int x = %d, y = %d;
                    if x > 0 && y > 0 { print 1; } else { print 2; }
                    """, x, y);
            assertEquals(List.of(String.valueOf(expected)), run(src));
        }

        @ParameterizedTest(name = "x={0}, y={1} → ветка {2}")
        @CsvSource({
                "1, 1, 1",
                "1, 0, 1",
                "0, 1, 1",
                "0, 0, 2"
        })
        @DisplayName("if с ||")
        void ifWithOr(int x, int y, int expected) {
            String src = String.format("""
                    int x = %d, y = %d;
                    if x > 0 || y > 0 { print 1; } else { print 2; }
                    """, x, y);
            assertEquals(List.of(String.valueOf(expected)), run(src));
        }

        @Test
        @DisplayName("&& в условии: правая часть не выполняется, если левая ложна")
        void shortCircuitAnd() {
            assertEquals(List.of("0"), run("""
                    int x = 0;
                    int y = 1;
                    if x != 0 && y == 1 { x = 99; }
                    print x;
                    """));
        }

        @Test
        @DisplayName("|| в условии: левая истинна → результат истинен")
        void shortCircuitOr() {
            assertEquals(List.of("1"), run("""
                    int x = 0;
                    int y = 1;
                    if x == 0 || y == 2 { print 1; } else { print 2; }
                    """));
        }
    }

    // ==================================================================
    // Степень (**) и факториал (!)
    // ==================================================================

    @Nested
    @DisplayName("Степень и факториал")
    class MathOperators {

        // ---------------- Степень (**) ----------------

        @Test
        @DisplayName("2 ** 10 = 1024")
        void powerSimple() {
            assertEquals(List.of("1024"), run("""
                    int a = 2 ** 10;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Любое число в степени 0 равно 1")
        void powerZeroExponent() {
            assertEquals(List.of("1"), run("""
                    int a = 123 ** 0;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Степень 1 не меняет число")
        void powerOneExponent() {
            assertEquals(List.of("7"), run("""
                    int a = 7 ** 1;
                    print a;
                    """));
        }

        @Test
        @DisplayName("** правоассоциативен: 2 ** 3 ** 2 = 2 ** (3 ** 2) = 512")
        void powerRightAssociative() {
            assertEquals(List.of("512"), run("""
                    int a = 2 ** 3 ** 2;
                    print a;
                    """));
        }

        @Test
        @DisplayName("** связывает сильнее умножения: 2 * 3 ** 2 = 18")
        void powerBindsTighterThanMultiplication() {
            assertEquals(List.of("18"), run("""
                    int a = 2 * 3 ** 2;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Скобки меняют порядок: (2 * 3) ** 2 = 36")
        void powerWithParentheses() {
            assertEquals(List.of("36"), run("""
                    int a = (2 * 3) ** 2;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Степень со скобками в основании: (1 + 1) ** 5 = 32")
        void powerWithParenthesizedBase() {
            assertEquals(List.of("32"), run("""
                    int a = (1 + 1) ** 5;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Степень с переменными: x ** y")
        void powerWithVariables() {
            assertEquals(List.of("27"), run("""
                    int x = 3;
                    int y = 3;
                    print x ** y;
                    """));
        }

        @Test
        @DisplayName("Отрицательная степень — исключение в VM")
        void negativeExponentThrows() {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    run("""
                            int a = 2 ** -1;
                            print a;
                            """));
            assertTrue(ex.getMessage().contains("Отрицательная степень"),
                    "Ожидалось сообщение об отрицательной степени, получено: " + ex.getMessage());
        }

        // ---------------- Факториал (!) ----------------

        @ParameterizedTest(name = "{0}! = {1}")
        @CsvSource({
                "0, 1",
                "1, 1",
                "2, 2",
                "3, 6",
                "4, 24",
                "5, 120",
                "6, 720",
                "7, 5040"
        })
        @DisplayName("Факториал малых чисел")
        void factorialSmall(int n, int expected) {
            assertEquals(List.of(String.valueOf(expected)), run(String.format("""
                    int a = %d!;
                    print a;
                    """, n)));
        }

        @Test
        @DisplayName("Факториал выражения в скобках: (2 + 3)! = 120")
        void factorialOfParenthesized() {
            assertEquals(List.of("120"), run("""
                    int a = (2 + 3)!;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Факториал переменной")
        void factorialOfVariable() {
            assertEquals(List.of("120"), run("""
                    int n = 5;
                    print n!;
                    """));
        }

        @Test
        @DisplayName("! связывает сильнее умножения: 2 * 3! + 1 = 13")
        void factorialBindsTighterThanMultiplication() {
            assertEquals(List.of("13"), run("""
                    int a = 2 * 3! + 1;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Двойной факториал: (3!)! = 720")
        void doubleFactorial() {
            assertEquals(List.of("720"), run("""
                    int a = (3!)!;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Факториал отрицательного числа — исключение в VM")
        void factorialOfNegativeThrows() {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    run("""
                            int a = 0 - 1;
                            print a!;
                            """));
            assertTrue(ex.getMessage().contains("Факториал отрицательного"),
                    "Ожидалось сообщение о факториале отрицательного, получено: " + ex.getMessage());
        }

        // ---------------- Совмещённые случаи ----------------

        @Test
        @DisplayName("Степень и факториал вместе: 2 ** 3! = 2 ** 6 = 64")
        void powerAndFactorialTogether() {
            assertEquals(List.of("64"), run("""
                    int a = 2 ** 3!;
                    print a;
                    """));
        }

        @Test
        @DisplayName("Факториал суммы, затем степень: (2 + 1)! ** 2 = 6 ** 2 = 36")
        void factorialThenPower() {
            assertEquals(List.of("36"), run("""
                    int a = (2 + 1)! ** 2;
                    print a;
                    """));
        }

        // ---------------- Регрессия: ! не ломает != ----------------

        @Test
        @DisplayName("!= не должен разбираться как факториал + присваивание")
        void notEqualNotBrokenByFactorial() {
            assertEquals(List.of("1"), run("""
                    int x = 3;
                    int y = 4;
                    if x != y { print 1; } else { print 2; }
                    """));
        }

        @Test
        @DisplayName("!= в while-условии работает как сравнение")
        void notEqualInWhile() {
            assertEquals(List.of("3"), run("""
                    int i = 0;
                    while i != 3 { i = i + 1; }
                    print i;
                    """));
        }
      // ---------------- Проверка сгенерированных опкодов ----------------

        @Test
        @DisplayName("Для ** генерируется инструкция POW")
        void generatesPowOpcode() {
            List<SimpleBytecodeCompiler.Instruction> code = compile("""
                    int a = 2 ** 3;
                    print a;
                    """);
            assertTrue(indexOf(code, SimpleBytecodeCompiler.Op.POW) >= 0,
                    "Должна быть инструкция POW");
        }

        @Test
        @DisplayName("Для ! генерируется инструкция FACT")
        void generatesFactOpcode() {
            List<SimpleBytecodeCompiler.Instruction> code = compile("""
                    int a = 5!;
                    print a;
                    """);
            assertTrue(indexOf(code, SimpleBytecodeCompiler.Op.FACT) >= 0,
                    "Должна быть инструкция FACT");
        }

        @Test
        @DisplayName("Для 2 * 3 генерируется MUL, а не POW")
        void multiplicationIsNotConfusedWithPower() {
            List<SimpleBytecodeCompiler.Instruction> code = compile("""
                    int a = 2 * 3;
                    print a;
                    """);
            assertTrue(indexOf(code, SimpleBytecodeCompiler.Op.MUL) >= 0,
                    "Должна быть инструкция MUL");
            assertTrue(indexOf(code, SimpleBytecodeCompiler.Op.POW) < 0,
                    "Не должно быть POW для обычного умножения");
        }
    }
}