package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.List;

abstract class Expr extends Lexeme {
  abstract public Object evaluateWith(Visitor<Object> visitor);

  interface Visitor<T> { 
    public T evalAssignExpr(Assign assign);
    public T evalBinaryExpr(Binary binary);
    public T evalEmptyExpr(Empty empty);
    public T evalCallExpr(Call func);
    public T evalGroupingExpr(Grouping grouping);
    public T evalLiteralExpr(Literal literal);
    public T evalLogicalExpr(Logical logical);
    public T evalPropertyExpr(Property get);
    public T evalThisExpr(This logical);
    public T evalUnaryExpr(Unary unary);
    public T evalVarExpr(Var variable);
  };
}

class Empty extends Expr {
  Empty() {
    this._printables = Arrays.asList();
  }

  @Override
  public String toString() {
    return "[]";
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalEmptyExpr(this);
  }
}


class Binary extends Expr {
  Binary(Expr left, Token operator, Expr right) {
    /* Binary operator expressions. e.g. 1+2 */
    this.left = left;
    this.operator = operator;
    this.right = right;
    this._printables = Arrays.asList(left, operator, right);
  }

  final Expr left;
  final Token operator;
  final Expr right;

  @Override
  public String toString() {
    return "" + left + operator + right;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalBinaryExpr(this);
  }
}

class Logical extends Expr {
  Logical(Expr left, Token operator, Expr right) {
    this.left = left;
    this.operator = operator;
    this.right = right;
    this._printables = Arrays.asList(left, operator, right);
  }

  final Expr left;
  final Token operator;
  final Expr right;

  @Override
  public String toString() {
    return "" + left + operator + right;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalLogicalExpr(this);
  }
}

class Unary extends Expr {
  Unary(Token operator, Expr expr) {
    this.operator = operator;
    this.expr = expr;
    this._printables = Arrays.asList(operator, expr);
  }

  final Token operator;
  final Expr expr;

  @Override
  public String toString() {
    return "" + operator + expr;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalUnaryExpr(this);
  }
}

class Grouping extends Expr {
  Grouping(Expr expr) {
    this.expr = expr;
    this._printables = Arrays.asList(expr);
  }

  final Expr expr;

  @Override
  public String toString() {
    return "" + expr;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalGroupingExpr(this);
  }
}

class Literal extends Expr {
  Literal(Object value) {
    this.value = value;
    this._printables = Arrays.asList();
  }

  final Object value;

  @Override
  public String toString() {
    return "" + value;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalLiteralExpr(this);
  }
}

class Var extends Expr {
  Var(Token name) {
    this.name = name;
    this._printables = Arrays.asList();
  }

  final Token name;

  @Override
  public String toString() {
    return "" + name;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalVarExpr(this);
  }
}

class Assign extends Expr {
  Assign(Expr assignee, Token token, Expr value) {
    this.assignee = assignee;
    this.token = token;
    this.value = value;
    this._printables = Arrays.asList(assignee, value);
  }

  // This token is used for error handling purposes.
  final Token token; 
  final Expr assignee;
  final Expr value;

  @Override
  public String toString() {
    return "" + assignee + "=" + value;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalAssignExpr(this);
  }
}

class Call extends Expr {
  Call(Token token, Expr callee, Series arguments) {
    this.token = token;
    this.callee = callee;
    this.arguments = arguments;
    this._printables = Arrays.asList(callee, arguments);
  }

  // The token is used for error handling purposes.
  final Token token;
  final Expr callee;
  final Series arguments;

  @Override
  public String toString() {
    return "" + callee + "(" + arguments + ")";
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalCallExpr(this);
  }
}


class This extends Expr {
  This(Token token) {
    this.token = token;
    this._printables = Arrays.asList(token);
  }

  final Token token;

  @Override
  public String toString() {
    return "" + token;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalThisExpr(this);
  }
}


class Property extends Expr {
  Property(Expr left, Token right) {
    this.left = left;
    this.right = right;
    this._printables = Arrays.asList(left, right);
  }

  final Expr left;
  final Token right;

  @Override
  public String toString() {
    return "" + left + "." + right;
  }

  @Override
  public Object evaluateWith(Expr.Visitor<Object> visitor) {
    return visitor.evalPropertyExpr(this);
  }
}


class Series<T extends Expr> extends Lexeme {
  Series(List<T> members) {
    this.members = members;
    this._printables.addAll(members);
  }

  final List<T> members;

  public T get(int index) {
    return members.get(index);
  }

  public int size() {
    return members.size();
  }

  @Override
  public String toString() {
    return "" + members;
  }
}
/*
Goal: For numerical/boolean expressions, return all satisfying trees

expression -> literal | grouping | binary | unary

grouping -> LEFT_PAREN expression RIGHT_PAREN
unary -> (MINUS | BANG) expression
binary -> expression INFIX_OP expression 
literal -> NUMBER | BOOLEAN

BOOLEAN -> TRUE FALSE
INFIX_OP -> + - * / != == < <= > >= AND OR

1 - (2 * 3) < 4 == false
*/
