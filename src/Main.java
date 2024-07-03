import annotations.ArgumentsContainer;
import annotations.fields.Argument;
import annotations.fields.NotRequired;
import parser.ArgumentParser;
import parser.exception.ArgumentParserException;

@ArgumentsContainer
class Arguments {

    @Argument(value = "--name", messageError = "Argument --name must be string")
    private String name;

    @Argument(value = "--second-name", messageError = "Argument --second-name must be string")
    @NotRequired
    private String secondName;

    @Argument(value = "--age", messageError = "Argument --age must be string")
    private int age;


    @Override
    public String toString() {
        return String.format("Name = %s\nSecond name = %s\nAge = %d", name, secondName, age);
    }
}


public class Main {
    public static void main(final String... args)  {
        try {
            Object obj = ArgumentParser.parseArguments(Arguments.class, args);
            System.out.println(obj);

        } catch (final ArgumentParserException e) {
            System.err.println(e.getMessage());
        }
    }
}