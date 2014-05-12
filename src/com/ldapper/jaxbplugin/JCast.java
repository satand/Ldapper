package com.ldapper.jaxbplugin;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JExpressionImpl;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JType;

final class JCast extends JExpressionImpl {

	private final JType type;

	private final JExpression object;

	JCast(JType type, JExpression object) {
		this.type = type;
		this.object = object;
	}

	public void generate(JFormatter f) {
		f.p("((").g(type).p(')').g(object).p(')');
	}
}