/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */

/* Generated By:JJTree: Do not edit this line. ASTRoot.java */

package com.tc.aspectwerkz.expression.ast;

public class ASTRoot extends SimpleNode {
  public ASTRoot(int id) {
    super(id);
  }

  public ASTRoot(ExpressionParser p, int id) {
    super(p, id);
  }

  /**
   * Accept the visitor. *
   */
  public Object jjtAccept(ExpressionParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}
