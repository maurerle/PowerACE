package tools.other;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Methods to set values for private fields, useful for testing.
 *
 *
 * @author
 *
 */
public class AccessFields {

	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory.getLogger(AccessFields.class.getName());

	/**
	 *
	 * Method to set values for private fields.
	 *
	 * @param object
	 * @param originalClass
	 * @param value
	 * @param name
	 */
	public static void setFieldViaReflection(Class<?> object, Object originalClass, Object value,
			String name) {
		try {
			final Field field = object.getDeclaredField(name);
			field.setAccessible(true);
			try {
				field.set(originalClass, value);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				logger.error(e.getMessage());
			}
		} catch (final NoSuchFieldException e) {
			if (object.getSuperclass() != null) {
				AccessFields.setFieldViaReflection(object.getSuperclass(), originalClass, value,
						name);
			} else {
				logger.error("Could not set field " + name + " with value " + value);
			}
		} catch (final SecurityException e) {
			logger.error(e.getMessage());
		}
	}

	/**
	 * Modify static fields (even private and final).
	 *
	 * @param object
	 * @param name
	 * @param value
	 */
	public static void setStaticFieldviaReflection(Class<?> object, String name, Object value) {

		try {
			final Field field = object.getDeclaredField(name);
			field.setAccessible(true);
			final Field modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
			field.set(null, value);
		} catch (final NoSuchFieldException e) {
			if (object.getSuperclass() != null) {
				AccessFields.setStaticFieldviaReflection(object.getSuperclass(), name, value);
			} else {
				logger.error("Could not set field " + name + " with value " + value);
			}
		} catch (final IllegalArgumentException e) {
			logger.error(e.getMessage());
		} catch (final IllegalAccessException e) {
			logger.error(e.getMessage());
		}
	}

}