package com.ldapper.schema.opends;

import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import com.ldapper.data.LDAPPERBean;

public class LdapSchemaTask extends Task {

	private String beanClasses;
	private String outputFileName;
	private int startObjOid;
	private int startAttrOid;

	public void setBeanClasses(String beanClasses) {
		this.beanClasses = beanClasses;
	}

	public void setOutputFileName(String outputFileName) {
		this.outputFileName = outputFileName;
	}

	public void setStartObjOid(String startObjOid) {
		this.startObjOid = Integer.parseInt(startObjOid);
	}

	public void setStartAttrOid(String startAttrOid) {
		this.startAttrOid = Integer.parseInt(startAttrOid);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void execute() throws BuildException {
		super.execute();

		log("Building ldap schemas...");
		long t = System.currentTimeMillis();

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

		File file = new File(getProject().getBaseDir(), outputFileName);
		try {
			LDAPPERBeanToLdapSchema ldapperBeanToLdapSchema =
					new LDAPPERBeanToLdapSchema(beanClassesSet, startObjOid,
							startAttrOid);

			FileWriter fileWriter = new FileWriter(file);

			fileWriter.append("dn: cn=schema\r\n");
			fileWriter.append("objectclass: top\r\n");
			fileWriter.append("objectClass: ldapSubentry\r\n");
			fileWriter.append("objectClass: subschema\r\n");
			fileWriter.append("# Italtel LDAP Schema \r\n");
			fileWriter.append("# \r\n");
			fileWriter.append("# AttributeTypes definition \r\n");
			fileWriter.append("# \r\n");

			// scrivo tutti gli attributeType
			for (SchemaAttribute schemaAttribute : ldapperBeanToLdapSchema
					.getSchemaAttributes()) {
				fileWriter.append(schemaAttribute.toSchemaString());
			}

			fileWriter.append("# \r\n");
			fileWriter.append("# ObjectClasses definition \r\n");
			fileWriter.append("# \r\n");

			// scrivo tutte le objectClass in ordine di gerarchia
			for (SchemaObjectClass schemaObjectClass : ldapperBeanToLdapSchema
					.getSchemaObjectClasses()) {
				fileWriter.append(schemaObjectClass.toSchemaString());
			}

			fileWriter.append("# \r\n");
			fileWriter.append("# End of schema \r\n");
			fileWriter.append("# \r\n");
			// chiudo il writer
			fileWriter.flush();
			fileWriter.close();

			t = System.currentTimeMillis() - t;
			log("Ldap schemas created in " + t + " ms");
		}
		catch (Exception e) {
			log("Error generating LDAP Schema output file");
			e.printStackTrace();
			throw new BuildException(e);
		}

	}
}
