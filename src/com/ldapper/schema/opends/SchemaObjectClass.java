package com.ldapper.schema.opends;

import java.util.List;
import java.util.Vector;

import com.ldapper.context.template.ExternalObjTemplate;

public class SchemaObjectClass {

	public enum TYPE {
		ABSTRACT,
		STRUCTURAL,
		AUXILIARY
	}

	private String oid;
	private String name;
	private String desc;
	private List<String> sups = new Vector<String>();
	private List<String> aux = new Vector<String>();
	private List<String> must = new Vector<String>();
	private List<String> may = new Vector<String>();
	private TYPE type;

	public SchemaObjectClass(ExternalObjTemplate eot) {

		name = eot.getLDAPObjectName();

		type = eot.isAbstract() ? TYPE.ABSTRACT : TYPE.STRUCTURAL;

		// gestisco i superior
		sups.addAll(eot.getLDAPObjectSup());
		String sup = eot.getSuperiorLDAPObjectName();
		if (sup != null && sup.trim().length() > 0) {
			sups.add(sup);
		}

		// gestisco le auxiliary
		aux.addAll(eot.getLDAPObjectAuxiliaryList());

		// set description
		desc = eot.getLDAPObjectDescription();
	}

	public TYPE getType() {
		return type;
	}

	public void setOid(String oid) {
		this.oid = oid;
	}

	public String getOid() {
		return oid;
	}

	public String getName() {
		return name;
	}

	public List<String> getSups() {
		return sups;
	}

	public List<String> getMust() {
		return must;
	}

	public List<String> getMay() {
		return may;
	}

	public String toSchemaString() {
		String CR = "\r\n";
		String TAB = "  ";

		StringBuffer buffer = new StringBuffer();

		buffer.append("objectClasses: ( ").append(oid).append(' ').append(CR);
		buffer.append(TAB).append("NAME").append(" '").append(name).append('\'')
				.append(CR);

		if (desc != null && desc.length() > 0) {
			buffer.append(TAB).append("DESC").append(" '").append(desc).append('\'')
					.append(CR);
		}

		if (sups.size() > 0) {
			buffer.append(TAB).append("SUP ( ");
			for (String sup : sups) {
				buffer.append(sup).append(" $ ");
			}
			// rimuovo l'ultimo dollaro
			buffer.setLength(buffer.length() - 2);

			buffer.append(") ").append(CR);
		}

		if (type != null) {
			buffer.append(TAB).append(type).append(CR);
		}

		if (must.size() > 0) {
			buffer.append(TAB).append("MUST").append(" ( ");
			for (String attr : must) {
				buffer.append(attr).append(" $ ");
			}
			// rimuovo l'ultimo dollaro
			buffer.setLength(buffer.length() - 2);

			buffer.append(')').append(CR);
		}

		if (may.size() > 0) {
			buffer.append(TAB).append("MAY").append(" ( ");
			for (String attr : may) {
				buffer.append(attr).append(" $ ");
			}
			// rimuovo l'ultimo dollaro
			buffer.setLength(buffer.length() - 2);

			buffer.append(')').append(CR);
		}

		buffer.append(TAB).append("X-ORIGIN 'ITALTEL S.p.A'").append(CR);
		buffer.append(TAB).append(")").append(CR);

		if (aux.size() > 0) {
			buffer.append("dITContentRules: ( ").append(oid).append(' ').append(CR);

			buffer.append(TAB).append("NAME").append(" '").append(name)
					.append("ContentRule").append('\'').append(CR);

			buffer.append(TAB).append("AUX").append(" ( ");
			for (String auxClass : aux) {
				buffer.append(auxClass).append(" $ ");
			}
			// rimuovo l'ultimo dollaro
			buffer.setLength(buffer.length() - 2);
			buffer.append(')').append(CR);

			buffer.append(TAB).append(")").append(CR);
		}

		return buffer.toString();
	}
}
