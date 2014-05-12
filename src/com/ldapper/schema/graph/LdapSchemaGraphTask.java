package com.ldapper.schema.graph;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import com.ldapper.annotations.LDAPObjectId;
import com.ldapper.context.template.AttributeTemplate;
import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.context.template.NestedObjTemplate;
import com.ldapper.context.template.ObjectTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.ObjType;

public class LdapSchemaGraphTask extends Task {

	private String beanClasses;
	private String outputDirName;
	private String outputFileName;

	private FileWriter rootWriter;

	public void setBeanClasses(String beanClasses) {
		this.beanClasses = beanClasses;
	}

	public void setOutputDirName(String outputDirName) {
		this.outputDirName = outputDirName;
	}

	public void setOutputFileName(String outputFileName) {
		this.outputFileName = outputFileName;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void execute() throws BuildException {
		super.execute();

		log("Building LDAP Schema Graph ...");
		long t = System.currentTimeMillis();

		File file =
				new File(getProject().getBaseDir(), outputDirName
						+ System.getProperty("file.separator") + outputFileName
						+ ".txt");

		Set<Class<? extends LDAPPERBean>> beanClassesSet =
				new HashSet<Class<? extends LDAPPERBean>>();
		String[] beanClasseNames = beanClasses.trim().split(",");
		try {
			for (String beanClassName : beanClasseNames) {
				beanClassesSet.add((Class<? extends LDAPPERBean>) Class
						.forName(beanClassName));
			}
		}
		catch (Exception e) {
			log("Error getting beanClasses: " + beanClasses.trim());
			throw new BuildException(e);
		}

		try {
			rootWriter = new FileWriter(file);
			rootWriter.append("# \r\n");
			rootWriter.append("# LDAP Schema Graph \r\n");
			rootWriter.append("# \r\n \r\n");

			ExternalObjTemplate eot;
			for (Class<? extends LDAPPERBean> beanClass : beanClassesSet) {
				eot = ExternalObjTemplate.getInstance(beanClass);
				drowGraphObjectClass(eot, 0);
				rootWriter.append("\r\n\r\n");
			}

			rootWriter.append("# \r\n");
			rootWriter.append("# End of LDAP Schema Graph \r\n");
			rootWriter.append("# \r\n \r\n");

			rootWriter.flush();
			rootWriter.close();

			t = System.currentTimeMillis() - t;
			log("LDAP Schema Graph created in " + t + " ms");
		}
		catch (Exception e) {
			log("Error generating LDAP Schema Graph");
			throw new BuildException(e);
		}

	}

	private String drowGraphObjectClass(ExternalObjTemplate eot, int indentCount)
			throws Exception {
		String nodeName = eot.getExternalObjClass().getSimpleName();

		File file =
				new File(getProject().getBaseDir(), outputDirName
						+ System.getProperty("file.separator") + nodeName + ".txt");

		FileWriter writer = new FileWriter(file);
		writer.append("# \r\n");
		writer.append("# LDAP Schema Graph of " + nodeName + "\r\n");
		writer.append("# \r\n \r\n");

		String node = drowGraphNode(eot);
		writeLine(node, writer);
		node += " file graph '" + nodeName + ".txt'";
		writeLine(node, rootWriter, indentCount);

		for (AttributeTemplate at : eot.getObjAttributesTemplates()) {
			writeLine(drowGraphAttribute(at), writer, 1);
		}

		ObjectTemplate ot = eot.getObjTemplate();
		if (ot != null) {
			writeLine(drowGraphObject(ot), writer, 1);
		}

		indentCount++;
		String nestedNode;
		for (NestedObjTemplate not : eot.getNestedObjTemplates()) {
			nestedNode =
					drowGraphObjectClass(not.getExternalObjTemplate(), indentCount);
			writeLine(nestedNode, writer, 1);
		}

		writer.append("# \r\n");
		writer.append("# End of LDAP Schema Graph \r\n");
		writer.append("# \r\n \r\n");

		writer.flush();
		writer.close();

		return node;
	}

	private String drowGraphNode(ExternalObjTemplate eot) throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append(">> ").append(eot.getExternalObjClass().getSimpleName())
				.append(" (");
		sb.append('\'').append(eot.getLDAPObjectName()).append('\'');

		List<String> supList = eot.getLDAPObjectSup();
		if (supList.size() > 0) {
			sb.append(" - ");
			for (Iterator<String> iterator = supList.iterator(); iterator.hasNext();) {
				sb.append('"').append(iterator.next()).append('"');
				if (iterator.hasNext()) {
					sb.append(", ");
				}
			}
		}
		List<String> auxList = eot.getLDAPObjectAuxiliaryList();
		if (auxList.size() > 0) {
			sb.append(" - ");
			for (Iterator<String> iterator = auxList.iterator(); iterator.hasNext();) {
				sb.append('\'').append(iterator.next()).append('\'');
				if (iterator.hasNext()) {
					sb.append(", ");
				}
			}
		}
		String desc = eot.getLDAPObjectDescription();
		if (desc != null && desc.trim().length() > 0) {
			sb.append(" - \"").append(desc).append('"');
		}
		sb.append(')');

		return sb.toString();
	}

	private String drowGraphObject(ObjectTemplate objTemplate) throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append(objTemplate.getField().getName());
		if (checkIfObjIsMust(objTemplate)) {
			sb.append('*');
		}
		String desc = objTemplate.getObjectDescription();
		if (desc != null && desc.trim().length() > 0) {
			sb.append(" (\"").append(desc).append("\")");
		}

		return sb.toString();
	}

	private boolean checkIfObjIsMust(ObjectTemplate objTemplate) {
		// Check if present XmlElement annotation with required true
		XmlElement xmlElement =
				objTemplate.getField().getAnnotation(XmlElement.class);
		if (xmlElement != null && xmlElement.required()) {
			return true;
		}
		return false;
	}

	private String drowGraphAttribute(AttributeTemplate attrTemplate)
			throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append(attrTemplate.getField().getName());
		if (checkIfAttrIsMust(attrTemplate)) {
			sb.append('*');
		}
		sb.append(" (");
		if (checkIfIsAttrID(attrTemplate)) {
			sb.append("RDN - ");
		} else if (attrTemplate.attributeIsOperational()) {
			sb.append("Operational attribute (read only) - ");
		}
		for (Iterator<String> iterator =
				attrTemplate.getAttributeAliases().iterator(); iterator.hasNext();) {
			sb.append('\'').append(iterator.next()).append('\'');
			if (iterator.hasNext()) {
				sb.append(", ");
			}
		}
		if (attrTemplate.getManagedObjClassType() != ObjType.SIMPLE) {
			sb.append(" - LIST");
		}
		String sup = attrTemplate.getSup();
		if (sup != null && sup.trim().length() > 0) {
			sb.append(" - SUP ").append(sup);
		}
		String desc = attrTemplate.getAttributeDescription();
		if (desc != null && desc.trim().length() > 0) {
			sb.append(" - \"").append(desc).append('"');
		}
		sb.append(')');

		return sb.toString();
	}

	private boolean checkIfIsAttrID(AttributeTemplate attrTemplate) {
		return attrTemplate.getField().isAnnotationPresent(LDAPObjectId.class);
	}

	private boolean checkIfAttrIsMust(AttributeTemplate attrTemplate) {
		// If this is the attribute ID then return true
		if (checkIfIsAttrID(attrTemplate)) {
			return true;
		}

		// If this is a operational attribute then return false
		if (attrTemplate.attributeIsOperational()) {
			return false;
		}

		// Check if present XmlElement annotation with required true
		XmlElement xmlElement =
				attrTemplate.getField().getAnnotation(XmlElement.class);
		if (xmlElement != null && xmlElement.required()) {
			return true;
		}
		return false;
	}

	private void writeLine(String line, OutputStreamWriter writer, int indentCount)
			throws Exception {
		for (int i = 0; i < indentCount; i++) {
			writer.append("\t");
		}
		writeLine(line, writer);
	}

	private void writeLine(String line, OutputStreamWriter writer) throws Exception {
		writer.append(line).append("\r\n");
	}
}
