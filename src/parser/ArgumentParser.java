package parser;


import annotations.ArgumentsContainer;
import annotations.fields.BoolArgument;
import annotations.fields.NotRequired;
import annotations.fields.Argument;
import parser.exception.ArgumentParserException;
import parser.exception.ClassNotCorrectException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

public class ArgumentParser {

    private ArgumentParser() {

    }

    private static void handleClassAnnotation(final Class<?> clazz) {
        if (!clazz.isAnnotationPresent(ArgumentsContainer.class)) {
            final String message = String.format("Class %s must have annotation @ArgumentsContainer", clazz.getName());
            throw new ClassNotCorrectException(message);
        }
    }

    private static void checkTypeField(final Field[] fields, final Class<? extends Annotation> annotation, final Class<?> expectedType) {
        final Set<Field> stringAnnotation = Arrays.stream(fields).filter(field -> field.isAnnotationPresent(annotation)).collect(Collectors.toSet());
        for (final Field field : stringAnnotation) {
            final Class<?> type = field.getType();
            if (type != expectedType) {
                final String message = String.format("Field %s must be %s, but it is %s.", field.getName(), annotation.getName(), type.getName());
                throw new ClassNotCorrectException(message);
            }
        }
    }

    private static void checkFields(final Field[] fields) {
        //checkTypeField(fields, Argument.class, String.class);
        checkTypeField(fields, BoolArgument.class, boolean.class);
    }



    private static Field[] getAllFields(final Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Argument.class) || field.isAnnotationPresent(BoolArgument.class))
                .toArray(Field[]::new);
    }


    private static String getKeyFromField(final Field field) {
        /// FIXME: I don't know how to fix it...

        if (field.isAnnotationPresent(Argument.class)) {
            return field.getDeclaredAnnotation(Argument.class).value();
        } else if (field.isAnnotationPresent(BoolArgument.class)) {
            return field.getDeclaredAnnotation(BoolArgument.class).value();
        }

        throw new AssertionError("Unexpected error in parser. Cause: Not map new Argument type.");
    }


    private static Map<String, Field> getMapStringToField(final Field[] fields) {
        final Map<String, Field> map = new HashMap<>(fields.length);
        for (final Field field : fields) {
            final String key = getKeyFromField(field);

            /// TODO: Create normal exception for this case. Write readable exception message
            if (map.containsKey(key)) {
                throw new ClassNotCorrectException("Same key for fields " + field.getName() + " " + map.get(key).getName());
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
            } else if (field.isAnnotationPresent(Argument.class)) {
                if (!field.isAnnotationPresent(NotRequired.class)) {
                    final String message = String.format("No required argument: %s\nDescription= %s", pair.getKey(), getDescriptionField(field));
                    throw new ArgumentParserException(message);
                }
            }
        }
    }

    /// TODO: make normal exception for different situations, write normal exception message
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
            throw new ClassNotCorrectException("@ArgumentsContainer must be class");
        } catch (final InvocationTargetException e) {
            throw new ClassNotCorrectException("Constructor of @ArgumentsContainer mustn't throw any exceptions");
        } catch (final NoSuchMethodException e) {
            throw new ClassNotCorrectException("@ArgumentsContainer must have constructor without parameters");
        }  catch (final IllegalAccessException e) {
            throw new AssertionError("Not expected error");
        }
    }

    private static String getDescriptionField(final Field field) {
        if (field.isAnnotationPresent(Argument.class)) {
            return field.getDeclaredAnnotation(Argument.class).messageError();
        }
        throw new AssertionError("Not expected error");
    }


    private static void setObjectFields(final Object obj, final Container container) throws ArgumentParserException {
        for (Map.Entry<Field, String> pair : container.notFlags.entrySet()) {
            final Field field = pair.getKey();
            final String value = pair.getValue();
            final Class<?> type = field.getType();
            try {
                field.setAccessible(true);

                if (type == String.class) {
                    field.set(obj, value);
                } else if (type == int.class) {
                    field.set(obj, Integer.parseInt(value));
                } else if (type == float.class) {
                    field.set(obj, Float.parseFloat(value));
                } else if (type == long.class) {
                    field.set(obj, Long.parseLong(value));
                } else {
                    throw new ClassNotCorrectException("This type of field not supported");
                    //throw new AssertionError("Not expected error.");
                }

            } catch (final NumberFormatException e) {
                final String message = String.format("%s\nBut argument was: %s", getDescriptionField(field), value);
                throw new ArgumentParserException(message);
            } catch (final IllegalAccessException e) {
                /// Not expected because accessible is true
                throw new AssertionError("Not expected error while set field");
            }
        }
    }

    public static Object parseArguments(final Class<?> clazz, final String[] args) throws ArgumentParserException {
        handleClassAnnotation(clazz);
        final Field[] allFields = getAllFields(clazz);
        checkFields(allFields);

        final Map<String, Field> stringToField = getMapStringToField(allFields);
        final Object obj = createObject(clazz);

        final Container container = createContainer(stringToField, args);
        setObjectFields(obj, container);

        return obj;
    }

    private record Container(Map<Field, String> notFlags, Map<Field, Boolean> flags) {
    }
}