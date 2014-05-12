package com.ldapper.data;

import com.ldapper.exception.InvalidBaseDN;

public interface IBaseDN {

    /**
     * Get object LDAP BaseDN (parental LDAP DN)
     * 
     * @return
     *     possible object is {@link String}
     * @throws Exception
     *     in case of incomplete LDAP BaseDN
     */
	public String getBaseDN() throws InvalidBaseDN;
	
    /**
     * Set object LDAP BaseDN (parental LDAP DN)
     * 
     * @param baseDN object LDAP BaseDN, allowed object is {@link String}
     * @throws Exception
     *     in case of invalid LDAP BaseDN
     */
	public void setBaseDN(String baseDN) throws InvalidBaseDN;
}
