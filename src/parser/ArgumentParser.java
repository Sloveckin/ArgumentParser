package parser;


import annotations.fields.*;
import parser.exception.ArgumentParserException;
import parser.exception.ClassNotCorrectException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ArgumentParser {


    private static final List<Class<? extends Annotation>> allAnnotations = List.of(
            Argument.class,
            BoolArgument.class,
            EnumArgument.class
    );


    private static final Map<Class<?>, Class<? extends Annotation>> mapping = Map.ofEntries(
            new AbstractMap.SimpleEntry<>(int.class, Argument.class),
            new AbstractMap.SimpleEntry<>(long.class, Argument.class),
            new AbstractMap.SimpleEntry<>(float.class, Argument.class),
            new AbstractMap.SimpleEntry<>(double.class, Argument.class),
            new AbstractMap.SimpleEntry<>(String.class, Argument.class),
            new AbstractMap.SimpleEntry<>(boolean.class, BoolArgument.class),
            new AbstractMap.SimpleEntry<>(Enum.class, EnumArgument.class)
    );


    private static final Map<Class<?>, Function<String, Object>> classToFunction = Map.ofEntries(
            new AbstractMap.SimpleEntry<>(int.class, Integer::parseInt),
            new AbstractMap.SimpleEntry<>(long.class, Long::parseLong),
            new AbstractMap.SimpleEntry<>(float.class, Float::parseFloat),
            new AbstractMap.SimpleEntry<>(double.class, Double::parseDouble),
            new AbstractMap.SimpleEntry<>(String.class, str -> str)
    );


    private ArgumentParser() {

    }

    private static void handleClassAnnotation(final Class<?> clazz) {
        if (!clazz.isAnnotationPresent(annotations.Container.class)) {
            final String message = String.format("Class %s must have annotation @Container", clazz.getName());
            throw new ClassNotCorrectException(message);
        }
    }

    private static boolean stringIsValidEnum(final Class<?> clazz, final String str) {
        try {
            final Method method = clazz.getMethod("valueOf", String.class);
            method.setAccessible(true);
            method.invoke(null, str);
            return true;

        } catch (final InvocationTargetException ignored) {
            return false;
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Not expected error. Cause: " + e.getMessage());
        }
    }

    private static void checkEnums(final Set<Field> fields) {
        for (final Field field : fields) {
            final EnumArgument annotation = field.getDeclaredAnnotation(EnumArgument.class);

            for (final MapPair pair : annotation.mapping()) {
                if (!stringIsValidEnum(field.getType(), pair.enumValue())) {
                    final String message = String.format("Enum field %s has incorrect mapping value: %s", field.getName(), pair.enumValue());
                    throw new ClassNotCorrectException(message);
                }
            }
        }
    }

    private static void checkFields1(final Field[] fields) {
        for (final Field field : fields) {
            final Class<?> type = field.getType();
            Class<? extends Annotation> annotation;
            if (type.isEnum()) {
                annotation = mapping.get(type.getSuperclass());
            } else {
                annotation = mapping.get(type);
            }

            if (!field.isAnnotationPresent(annotation)) {
                final String message = String.format("Field %s must be annotated with %s", field.getName(), annotation.getName());
                throw new ClassNotCorrectException(message);
            }
        }

        final Set<Field> enumFields = Arrays.stream(fields).filter(field -> field.isAnnotationPresent(EnumArgument.class)).collect(Collectors.toSet());
        checkEnums(enumFields);
    }


    private static boolean isNeedField(final Field field) {
        return allAnnotations.stream().anyMatch(field::isAnnotationPresent);
    }

    private static Field[] getAllFields(final Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(ArgumentParser::isNeedField)
                .toArray(Field[]::new);
    }


    private static String getKeyFromField(final Field field) {
        for (final Class<? extends Annotation> clazz : allAnnotations) {
            if (!field.isAnnotationPresent(clazz)) {
                continue;
            }
            final Annotation annotation = field.getDeclaredAnnotation(clazz);
            try {
                return (String) clazz.getMethod("value").invoke(annotation);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                /// In this situation we can catch Exception I think
                throw new AssertionError("Not expected error. Cause: " + e.getCause());
            }
        }

        throw new AssertionError("Unexpected error in parser. Cause: Not map new Argument type.");
    }


    private static Map<String, Field> getMapStringToField(final Field[] fields) {
        final Map<String, Field> map = new HashMap<>(fields.length);
        for (final Field field : fields) {
            final String key = getKeyFromField(field);

            if (map.containsKey(key)) {
                final String args = String.join(", ", field.getName(), map.get(key).getName());
                final String message = String.format("Arguments can't have same keys. { %s } have key = %s", args, key);
                throw new ClassNotCorrectException(message);
            }
            map.put(key, field);
        }
        return map;
    }


    private static void checkLeftFields(final Container container, final Map<String, Field> map) throws ArgumentParserException {
        for (final Map.Entry<String, Field> pair : map.entrySet()) {
            final Field field = pair.getValue();
            if (field.isAnnotationPresent(BoolArgument.class)) {
                final BoolArgument ann = field.getDeclaredAnnotation(BoolArgument.class);
                container.flags.put(field, ann.def());
            } else if (!field.isAnnotationPresent(NotRequired.class)) {
                final String message = String.format("No required argument: %s\nDescription= %s", pair.getKey(), getMessageError(field));
                throw new ArgumentParserException(message);
            }
        }
    }

    /// TODO: make normal exception for different situations
    private static Container createContainer(final Map<String, Field> map, final String[] args) throws ArgumentParserException {
        final Set<String> usedArguments = new HashSet<>(args.length);
        final Container container = new Container(new HashMap<>(), new HashMap<>());
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];

            if (!map.containsKey(arg)) {
                throw new ArgumentParserException("No expected argument: " + arg);
            }

            if (usedArguments.contains(arg)) {
                throw new ArgumentParserException("The argument is repeated: " + arg);
            }
            usedArguments.add(arg);

            final Field field = map.get(arg);
            final boolean isNotBool = field.getType() != boolean.class;

            if (isNotBool && i + 1 == args.length) {
                throw new ArgumentParserException("No value for argument: " + arg);
            } else if (isNotBool) {
                container.notFlags.put(field, args[++i]);
            } else {
                container.flags.put(field, true);
            }

            map.remove(arg);
        }

        checkLeftFields(container, map);
        return container;
    }

    private static Object createObject(final Class<?> clazz) {
        try {
            final Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (final InstantiationException e) {
            throw new ClassNotCorrectException("@Container must be class");
        } catch (final InvocationTargetException e) {
            throw new ClassNotCorrectException("Constructor of @Container mustn't throw any exceptions");
        } catch (final NoSuchMethodException e) {
            throw new ClassNotCorrectException("@Container must have constructor without parameters");
        } catch (final IllegalAccessException e) {
            throw new AssertionError("Not expected error. Cause: " + e.getCause());
        }
    }

    private static String getMessageError(final Field field) {
        try {
            for (final Class<? extends Annotation> clazz : allAnnotations) {
                if (!field.isAnnotationPresent(clazz)) {
                    continue;
                }
                final Annotation annotation = field.getDeclaredAnnotation(clazz);
                return (String) clazz.getMethod("messageError").invoke(annotation);
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new AssertionError("Not expected error. Cause: " + e.getCause());
        }
        throw new AssertionError("Not expected error. Cause: Field not annotated");
    }


    private static void setObjectFields(final Object obj, final Map<Field, String> map) throws ArgumentParserException {
        for (Map.Entry<Field, String> pair : map.entrySet()) {
            final Field field = pair.getKey();
            final String value = pair.getValue();
            final Class<?> type = field.getType();
            try {
                field.setAccessible(true);
                if (classToFunction.containsKey(type)) {
                    field.set(obj, classToFunction.get(type).apply(value));
                } else {

                    // Fix: Clean this piece of code...
                    final EnumArgument ann = field.getDeclaredAnnotation(EnumArgument.class);
                    for (final MapPair mapPair : ann.mapping()) {

                        if (!value.equals(mapPair.key())) {
                            continue;
                        }

                        try {
                            final Method method = field.getType().getMethod("valueOf", String.class);
                            method.setAccessible(true);
                            field.set(obj, method.invoke(null, mapPair.enumValue()));
                            return;
                        } catch (final NoSuchMethodException | InvocationTargetException e) {
                            throw new AssertionError("Not expected error. Cause: " + e.getCause());
                        }
                    }

                    /// Come here if value of enum flag not correct
                    throw new ArgumentParserException(getMessageError(field));
                }

            } catch (final NumberFormatException ignored) {
                final String message = String.format("%s\nValue was: %s", getMessageError(field), value);
                throw new ArgumentParserException(message);
            } catch (final IllegalAccessException e) {
                /// Not expected because accessible is true
                throw new AssertionError("Not expected error. Cause: " + e.getCause());
            }
        }
    }

    private static void setObjectBooleans(final Object obj, final Map<Field, Boolean> flags) {
        for (Map.Entry<Field, Boolean> pair : flags.entrySet()) {
            final Field field = pair.getKey();
            field.setAccessible(true);
            try {
                field.setBoolean(obj, pair.getValue());
            } catch (final IllegalAccessException e) {
                throw new AssertionError("Not expected error. Cause: " + e.getCause());
            }
        }
    }

    public static Object parseArguments(final Class<?> clazz, final String[] args) throws ArgumentParserException {
        handleClassAnnotation(clazz);
        final Field[] allFields = getAllFields(clazz);
        checkFields1(allFields);

        final Map<String, Field> stringToField = getMapStringToField(allFields);
        final Object obj = createObject(clazz);

        final Container container = createContainer(stringToField, args);
        setObjectFields(obj, container.notFlags);
        setObjectBooleans(obj, container.flags);

        return obj;
    }

    private record Container(Map<Field, String> notFlags, Map<Field, Boolean> flags) {
    }
}