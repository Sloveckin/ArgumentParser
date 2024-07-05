import annotations.Container;
import annotations.fields.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import parser.ArgumentParser;
import parser.exception.ArgumentParserException;
import parser.exception.ClassNotCorrectException;


@Container
class TestingClass1 {

    @Argument(value = "--intField", messageError = "Error in intField")
    @NotRequired
    public int intField;

    @Argument(value = "--floatField", messageError = "Error in floatField")
    @NotRequired
    public float floatField;

    @Argument(value = "--doubleField", messageError = "Error in doubleField")
    public double doubleField;

    @Argument(value = "--longField", messageError = "Error in longField")
    public long longField;

    @Argument(value = "--stringField", messageError = "Error in stringField")
    public String stringField;

    public TestingClass1() {

    }
}

enum TestEnum {
    X, Y, Z
}

@Container
class TestingClass3 {
    @EnumArgument(value = "--testEnum",
            messageError = "Error in testEnum",
            mapping = {
                    @MapPair(key = "-X", enumValue = "X"),
                    @MapPair(key = "-Y", enumValue = "Y"),
                    @MapPair(key = "-Z", enumValue = "Z")
            })
    public TestEnum testEnum;
}

@Container
class NotCorrectClass1 {
    @Argument(value = "--field", messageError = "Error in --field")
    private boolean field;
}

@Container
class NotCorrectClass2 {
    @BoolArgument("--field")
    private int field;
}

@Container
class NotCorrectClass3 {
    @EnumArgument(value = "--testEnum",
            messageError = "Error",
            mapping = {
                    @MapPair(key = "-X", enumValue = "-X"),
                    @MapPair(key = "-Z", enumValue = "-Y"),
                    @MapPair(key = "-Y", enumValue = "-Z"),
            })
    public TestEnum testEnum;
}


@Container
class TestingClass2 {
    @BoolArgument("--boolValue1")
    public boolean boolValue1;

    @BoolArgument("--boolValue2")
    public boolean boolValue2;

    @BoolArgument(value = "--missing1", def = true)
    public boolean missingValue1;

    @BoolArgument("--missing2")
    public boolean missingValue2;

}


public class ArgumentParserTest {

    private static String[] createArray(final String src) {
        return src.split(" ");
    }

    @Test
    @DisplayName("Test primitives")
    void test1() throws ArgumentParserException {
        final String commandLine = "--intField 25 --floatField 32.1f --doubleField 128.5 --longField 231243214231 --stringField Hello!";
        final TestingClass1 obj = ArgumentParser.parseArguments(TestingClass1.class, createArray(commandLine));
        Assertions.assertEquals(obj.intField, 25);
        Assertions.assertEquals(obj.floatField, 32.1f);
        Assertions.assertEquals(obj.doubleField, 128.5);
        Assertions.assertEquals(obj.longField, 231243214231L);
        Assertions.assertEquals(obj.stringField, "Hello!");
    }


    @Test
    @DisplayName("Test boolean")
    void test2() throws ArgumentParserException {
        final String commandLine = "--boolValue1 --boolValue2";
        final TestingClass2 obj = ArgumentParser.parseArguments(TestingClass2.class, createArray(commandLine));
        Assertions.assertTrue(obj.boolValue1);
        Assertions.assertTrue(obj.boolValue2);
        Assertions.assertTrue(obj.missingValue1);
        Assertions.assertFalse(obj.missingValue2);
    }

    @Test
    @DisplayName("Test enums")
    void test3() throws ArgumentParserException {
        final String commandLine = "--testEnum -Y";
        final TestingClass3 obj = ArgumentParser.parseArguments(TestingClass3.class, createArray(commandLine));
        Assertions.assertEquals(obj.testEnum, TestEnum.Y);
    }

    @Test
    @DisplayName("Test with not required arguments")
    void test4() throws ArgumentParserException {
        final String commandLine = "--doubleField 128.5 --longField 231243214231 --stringField Hello!";
        ArgumentParser.parseArguments(TestingClass1.class, createArray(commandLine));
    }

    @Test()
    @DisplayName("Catch type error")
    void test5() {
        final String commandLine = "--intField NotNumberField";
        final ArgumentParserException exception = Assertions.assertThrows(ArgumentParserException.class, () -> {
            ArgumentParser.parseArguments(TestingClass1.class, createArray(commandLine));
        });
        Assertions.assertNotNull(exception);
    }

    @Test()
    @DisplayName("Catch error when class not correct")
    void test6() {
        final String commandLine = "--field 25";
        final ClassNotCorrectException exception1 = Assertions.assertThrows(ClassNotCorrectException.class, () -> {
            ArgumentParser.parseArguments(NotCorrectClass1.class, createArray(commandLine));
        });
        Assertions.assertNotNull(exception1);


        final ClassNotCorrectException exception2 = Assertions.assertThrows(ClassNotCorrectException.class, () -> {
            ArgumentParser.parseArguments(NotCorrectClass2.class, createArray(commandLine));
        });
        Assertions.assertNotNull(exception2);
    }

    @Test
    @DisplayName("Catch error in not correct mapping enum")
    void test7() {
        final String commandLine = "--field 23";
        final ClassNotCorrectException exception = Assertions.assertThrows(ClassNotCorrectException.class, () -> {
            ArgumentParser.parseArguments(NotCorrectClass3.class, createArray(commandLine));
        });
        Assertions.assertNotNull(exception);
    }



}
