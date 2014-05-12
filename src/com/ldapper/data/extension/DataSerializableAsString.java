package com.ldapper.data.extension;

import java.util.regex.Pattern;

public interface DataSerializableAsString {
	
    /**
     * Get object string format
     * 
     * @return
     *     possible object is {@link String}
     */
	public String read();
	
    /**
     * Set object from his string format
     * 
     * @param object string format, allowed object is {@link String}
     */
	public void write(String value);
	
	
	 /**
     * Optionally get the pattern used to deserialize the obj
     * 
     * @return
     *     possible object is {@link Pattern}
     */
	public Pattern getDataSerializablePattern();
}

