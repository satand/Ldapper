package com.ldapper.schema.opends;

import java.util.List;
import java.util.regex.Pattern;

import com.ldapper.context.template.AttributeTemplate;
import com.ldapper.data.extension.DataSerializableAsString;

public class SchemaAttribute {

	private static final String CR = "\r\n";
	private static final String TAB = "  ";

	public enum MATCHING_RULE {
		caseIgnoreMatch,
		caseIgnoreSubstringsMatch,
		caseIgnoreOrderingMatch,
		caseExactMatch,
		caseExactOrderingMatch,
		caseExactSubstringsMatch,
		booleanMatch,
		booleanOrderingMatch
	}

	public enum SYNTAX {
		Boolean("1.3.6.1.4.1.1466.115.121.1.7"),
		DirectoryString("1.3.6.1.4.1.1466.115.121.1.15"),
		Integer("1.3.6.1.4.1.1466.115.121.1.27"),
		Binary("1.3.6.1.4.1.1466.115.121.1.5");

		private String oid;

		private SYNTAX(String oid) {
			this.oid = oid;
		}
	}

	private static final String SYNTAX_OID = "1.1.X";
	private static int nextSyntaxOid;

	private String oid;
	private List<String> aliases;
	private String sup;
	private String desc;
	private String syntax;
	private String equality;
	private String ordering;
	private String substr;
	private boolean singleValue = false;
	private boolean operational = false;

	private Pattern pattern;
	private String enumeration;

	public SchemaAttribute(AttributeTemplate attributeTemplate) {

		aliases = attributeTemplate.getAttributeAliases();
		desc = attributeTemplate.getAttributeDescription();
		operational = attributeTemplate.attributeIsOperational();

		switch (attributeTemplate.getManagedObjClassType()) {
			case SIMPLE:
				singleValue = true;
				break;
		}

		String value = attributeTemplate.getSyntax();
		if (value != null && value.trim().length() > 0 && !value.equals("empty")) {
			syntax = value;
		}
		value = attributeTemplate.getEquality();
		if (value != null && value.trim().length() > 0) {
			equality = value;
		}
		value = attributeTemplate.getOrdering();
		if (value != null && value.trim().length() > 0) {
			ordering = value;
		}
		value = attributeTemplate.getSubstr();
		if (value != null && value.trim().length() > 0) {
			substr = value;
		}
		value = attributeTemplate.getSup();
		if (value != null && value.trim().length() > 0) {
			sup = value;
		}

		switch (attributeTemplate.getBaseManagedObjType()) {
			case AUTOMATIC_DATA_SERIALIZABLE_AS_STRING:
			case DATA_SERIALIZABLE_AS_STRING:
				if (syntax == null) {
					try {
						pattern =
								((DataSerializableAsString) attributeTemplate
										.getBaseManagedObjClass().newInstance())
										.getDataSerializablePattern();
					}
					catch (Exception e) {
						System.out.println("UNABLE TO CREATE INLINE SYNTAX FOR "
								+ attributeTemplate.getAttributeName());
						e.printStackTrace();
					}

					if (pattern != null) {
						syntax = nextSyntaxOid();
					} else {
						syntax = SYNTAX.DirectoryString.oid;
					}
				}

				if (equality == null) {
					equality = MATCHING_RULE.caseIgnoreMatch.name();
				}
				if (ordering == null) {
					ordering = MATCHING_RULE.caseIgnoreOrderingMatch.name();
				}
				if (substr == null) {
					substr = MATCHING_RULE.caseIgnoreSubstringsMatch.name();
				}
				break;

			case ENUM:
				StringBuffer enumBuffer = new StringBuffer();
				for (Object enumObj : attributeTemplate.getBaseManagedObjClass()
						.getEnumConstants()) {
					enumBuffer.append('\'').append(enumObj).append("' ");
				}
				enumeration = enumBuffer.toString();

				if (syntax == null) {
					syntax = nextSyntaxOid();
				}
				if (equality == null) {
					equality = MATCHING_RULE.caseIgnoreMatch.name();
				}
				if (ordering == null) {
					ordering = MATCHING_RULE.caseIgnoreOrderingMatch.name();
				}
				if (substr == null) {
					substr = MATCHING_RULE.caseIgnoreSubstringsMatch.name();
				}
				break;
			case STRING:
			case CHAR:
				if (syntax == null) {
					syntax = SYNTAX.DirectoryString.oid;
				}
				if (equality == null) {
					equality = MATCHING_RULE.caseIgnoreMatch.name();
				}
				if (ordering == null) {
					ordering = MATCHING_RULE.caseIgnoreOrderingMatch.name();
				}
				if (substr == null) {
					substr = MATCHING_RULE.caseIgnoreSubstringsMatch.name();
				}
				break;

			// Boolean
			case BOOLEAN:
				if (syntax == null) {
					syntax = SYNTAX.Boolean.oid;
				}
				if (equality == null) {
					equality = MATCHING_RULE.booleanMatch.name();
				}
				if (ordering == null) {
					ordering = MATCHING_RULE.booleanOrderingMatch.name();
				}
				break;

			// Binary
			case BYTE:
				if (syntax == null) {
					syntax = SYNTAX.Binary.oid;
				}
				break;

			// Numbers
			case INTEGER:
			case LONG:
			case SHORT:
			case BIG_DECIMAL:
			case DOUBLE:
			case FLOAT:
				if (syntax == null) {
					syntax = SYNTAX.Integer.oid;
				}
				break;

			default:
				break;
		}

	}

	public void setOid(String oid) {
		this.oid = oid;
	}

	public String getOid() {
		return oid;
	}

	public String getName() {
		return aliases.get(0);
	}

	public String toSchemaString() {
		StringBuffer buffer = new StringBuffer();
		if (pattern != null) {
			buffer.append("ldapSyntaxes: ( ").append(syntax).append(' ').append(CR)
					.append(TAB).append("DESC '").append(aliases.get(0))
					.append(" syntax' ").append(CR).append(TAB)
					.append("X-PATTERN '").append(pattern.pattern()).append("' ")
					.append(CR).append(TAB).append(")").append(CR);
		} else if (enumeration != null) {
			buffer.append("ldapSyntaxes: ( ").append(syntax).append(' ').append(CR)
					.append(TAB).append("DESC '").append(aliases.get(0))
					.append(" enumeration' ").append(CR).append(TAB)
					.append("X-ENUM ( ").append(enumeration).append(')').append(CR)
					.append(TAB).append(")").append(CR);
		}

		buffer.append("attributeTypes: ( ").append(oid).append(' ').append(CR);

		buffer.append(TAB).append("NAME").append(" (");
		for (String alias : aliases) {
			buffer.append(" '").append(alias).append('\'');
		}
		buffer.append(" )").append(CR);

		if (desc != null && desc.length() > 0) {
			buffer.append(TAB).append("DESC").append(" '").append(desc).append('\'')
					.append(CR);
		}

		if (sup != null && sup.length() > 0) {
			buffer.append(TAB).append("SUP ").append(sup).append(" ").append(CR);
		}

		if (equality != null && equality.length() > 0 && !equality.equals("empty")) {
			buffer.append(TAB).append("EQUALITY").append(' ').append(equality)
					.append(CR);
		}

		if (substr != null && substr.length() > 0 && !substr.equals("empty")) {
			buffer.append(TAB).append("SUBSTR").append(' ').append(substr)
					.append(CR);
		}

		if (ordering != null && ordering.length() > 0 && !ordering.equals("empty")) {
			buffer.append(TAB).append("ORDERING").append(' ').append(ordering)
					.append(CR);
		}

		if (syntax != null && syntax.length() > 0) {
			buffer.append(TAB).append("SYNTAX").append(' ').append(syntax)
					.append(CR);
		}

		if (singleValue) {
			buffer.append(TAB).append("SINGLE-VALUE").append(CR);
		}

		if (operational) {
			buffer.append(TAB).append("NO-USER-MODIFICATION").append(CR);
			buffer.append(TAB).append("USAGE directoryOperation").append(CR);
		}

		buffer.append(TAB).append("X-ORIGIN 'ITALTEL S.p.A'").append(CR);

		buffer.append(TAB).append(")").append(CR);

		return buffer.toString();
	}

	private String nextSyntaxOid() {
		return SYNTAX_OID.replace("X", String.valueOf(nextSyntaxOid++));
	}

}
