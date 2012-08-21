package org.lumongo.fields;

import java.lang.reflect.Field;
import java.util.Collection;

import org.lumongo.cluster.message.Lumongo.LMAnalyzer;
import org.lumongo.cluster.message.Lumongo.LMField;

public class IndexedFieldInfo<T> {
	private String fieldName;
	private Field field;
	private LMAnalyzer lmAnalyzer;

	public IndexedFieldInfo(Field field, String fieldName, LMAnalyzer lmAnalyzer) {
		this.fieldName = fieldName;
		this.field = field;
		this.lmAnalyzer = lmAnalyzer;
	}

	public String getFieldName() {
		return fieldName;
	}

	public LMField build(Object object) throws IllegalArgumentException, IllegalAccessException {

		if (object != null) {
			LMField.Builder lmFieldBuilder = LMField.newBuilder();
			lmFieldBuilder.setFieldName(fieldName);
			Object o = field.get(object);

			if (o instanceof Collection<?>) {
				Collection<?> l = (Collection<?>) o;
				for (Object s : l) {
					addObjectData(lmFieldBuilder, s);
				}
			}
			else if (o.getClass().isArray()) {
				Object[] l = (Object[]) o;
				for (Object s : l) {
					addObjectData(lmFieldBuilder, s);
				}
			}
			else {
				addObjectData(lmFieldBuilder, o);
			}

			return lmFieldBuilder.build();
		}
		return null;
	}

	private void addObjectData(LMField.Builder lmFieldBuilder, Object o) {
		if (LMAnalyzer.NUMERIC_INT.equals(lmAnalyzer)) {
			lmFieldBuilder.addIntValue((Integer) o);
		}
		else if (LMAnalyzer.NUMERIC_LONG.equals(lmAnalyzer)) {
			lmFieldBuilder.addLongValue((Long) o);
		}
		else if (LMAnalyzer.NUMERIC_FLOAT.equals(lmAnalyzer)) {
			lmFieldBuilder.addFloatValue((Float) o);
		}
		else if (LMAnalyzer.NUMERIC_DOUBLE.equals(lmAnalyzer)) {
			lmFieldBuilder.addDoubleValue((Double) o);
		}
		else {
			lmFieldBuilder.addFieldValue((String) o);
		}
	}
}
