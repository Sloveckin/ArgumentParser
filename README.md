# Argument Parser

Простой парсер, позволяющий заполнять поля класса данными с командной строки.\
Разметка данных работает с помощью аннотаций.

# Аннотации

* @Container - аннотация для указания того, что класс будет использоваться для записи данных из командной строки
* @Argument - для пометки полей с типом *int, long, float, double и String*
* @BoolArgument - для пометки поля с типом *boolean*.
* @EnumArgument - для поментки *Enum* полей
* @NotRequired - помечается необязательный аргумет (При отсутствии такого необязательного аргумента не будет выбрасываться ошибка)

# Пример использования

## Код:
```java
@Container
class ArgumentClass {

    @Argument(value = "--field1", messageError = "Error in intField")
    private int intField;

    @BoolArgument("--flag")
    private boolean flag;

    @EnumArgument(value = "--realization", messageError = "Error in realization", mapping = {
            @MapPair(key = "0", enumValue = "ZERO"),
            @MapPair(key = "1", enumValue = "FIRST"),
            @MapPair(key = "2", enumValue = "SECOND"),
            @MapPair(key = "3", enumValue = "THIRD"),
    })
    private Realization realization;

    @Override
    public String toString() {
        return String.format("intField = %d\nflag = %b\nrealization = %s", intField, flag, realization);
    }
}

enum Realization {
    ZERO, FIRST, SECOND, THIRD
}


public class Main {
    public static void main(String[] args) throws ArgumentParserException {
        final ArgumentClass arg = ArgumentParser.parseArguments(ArgumentClass.class, args);
        System.out.println(arg);
    }
}

```
## Аргументы командной строки:
```
--field1 256 --realization 2
```

## Вывод:
```
intField = 256
flag = false
realization = SECOND
```