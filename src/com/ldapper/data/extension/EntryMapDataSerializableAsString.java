package com.ldapper.data.extension;

public interface EntryMapDataSerializableAsString<T> extends DataSerializableAsString {
	
    /**
     * Get object key
     * 
     * @return
     *     possible object is {@link T}
     */
	public T getKey();
}

