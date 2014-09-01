package org.lumongo.fields;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lumongo.util.CommonCompression;

import com.mongodb.DBObject;

public class SavedFieldInfo<T> {
	private final String fieldName;
	private final Field field;
	private boolean compressed;
	private boolean fieldIsList;
	
	public SavedFieldInfo(Field field, String fieldName, boolean compressed) {
		this.fieldName = fieldName;
		this.field = field;
		this.compressed = compressed;
		this.fieldIsList = List.class.isAssignableFrom(field.getType());
	}
	
	public String getFieldName() {
		return fieldName;
	}
	
	public boolean isCompressed() {
		return compressed;
	}
	
	Object getValue(T object) throws Exception {
		
		Object o = field.get(object);
		
		if (o != null && compressed) {
			if (String.class.equals(field.getType())) {
				String s = (String) o;
				o = CommonCompression.compressZlib(s.getBytes("UTF-8"), CommonCompression.CompressionLevel.NORMAL);
			}
			else if (byte[].class.equals(field.getType())) {
				byte[] b = (byte[]) o;
				o = CommonCompression.compressZlib(b, CommonCompression.CompressionLevel.NORMAL);
			}
		}
		
		return o;
	}
	
	public void populate(T newInstance, DBObject savedDBObject) throws Exception {
		
		Object value = savedDBObject.get(fieldName);
		if (value != null && compressed) {
			if (value instanceof byte[]) {
				byte[] b = (byte[]) value;
				if (String.class.equals(field.getType())) {
					field.set(newInstance, new String(CommonCompression.uncompressZlib(b), "UTF-8"));
					return;
				}
				else if (byte[].class.equals(field.getType())) {
					field.set(newInstance, CommonCompression.uncompressZlib(b));
					return;
				}
				
			}
			
		}
		
		boolean valuesIsList = value instanceof List;
		if (valuesIsList && !fieldIsList) {
			List<?> valueList = (List<?>) value;
			if (valueList.size() == 1) {
				Object first = valueList.iterator().next();
				if (first != null) {
					field.set(newInstance, first);
				}
			}
			else if (valueList.isEmpty()) {
				
			}
			else {
				throw new Exception("Cannot assign multiple values <" + valueList + "> to field <" + field.getName() + "> with type <" + field.getType()
								+ "> because it is not a list.");
			}
		}
		else if (!valuesIsList && fieldIsList) {
			if (value != null) {
				field.set(newInstance, Arrays.asList(value));
			}
		}
		else {
			if (value != null) {
				field.set(newInstance, new ArrayList<>((List<?>) value));
			}
		}
	}
}
