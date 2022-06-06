package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }
  List<Statement> parse() {
    List<Statement> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements; // [parse-error-handling]
  }
  private Statement returns() {
    try {
      if (match(RETURN)) {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after return statement.");
        return new ReturnStmt(expr);
      }
    } catch (ParseError error) {
      // not sure if there is a way to write this without repeating this catch block
      synchronize();
      return null;
    }
    return declaration();
  }
  private Statement declaration() {
    try {
      if (check(VAR)) return varDeclaration();
      if (match(FUN)) return funcDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }
  private Statement varDeclaration() {
    Token varToken = consume(VAR, "Expect keyword 'var'.");
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new VarStmt(name, initializer);
  }
  private Statement funcDeclaration() {
    List<Statement> statements = new ArrayList<>();

    Token identifier = consume(IDENTIFIER, "Expect function name.");
    consume(LEFT_PAREN, "Expect '(' after function name.");
    _GetExpression getParameter = () -> parameter();
    Series<Var> parameters = series(RIGHT_PAREN, getParameter);
    consume(LEFT_BRACE, "Expect '{' after function header.");
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(returns());
    }
    consume(RIGHT_BRACE, "Expect '}' after function body.");

    return new FuncStmt(new Var(identifier), parameters, new BlockStmt(statements));
  }
  private Var parameter() {
    if (match(IDENTIFIER)) {
      return new Var(previous());
    }
    throw error(peek(), "Expect identifier.");
  }
  private Statement statement() {
    if (match(PRINT)) return printStatement();
    if (match(LEFT_BRACE)) return new BlockStmt(block());
    if (match(IF)) return conditional();
    if (match(WHILE)) return whileLoop();
    if (match(FOR)) return forLoop();

    return expressionStatement();
  }
  private Statement printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new PrintStmt(value);
  }
  private Statement expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new ExprStmt(expr);
  }
  private List<Statement> block() {
    List<Statement> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE, "Expect '}' after blocYk.");
    return statements;
  }
  private Statement forLoop() {
    consume(LEFT_PAREN, "Expect '(' after 'for'.");
    Statement initializer = varDeclaration();
    Expr condition = expression();
    consume(SEMICOLON, "Expect ';' after 'for' condition.");
    Expr iterator = assignment();
    consume(RIGHT_PAREN, "Expect ')' after 'for'.");
    Statement body = statement();
    return new ForStmt(initializer, condition, new ExprStmt(iterator), body);
  }
  private Statement whileLoop() {
    Expr condition = primary();
    Statement statement = statement();
    return new WhileStmt(condition, statement);
  }
  private Statement conditional() {
    Expr condition = primary();
    Statement then = statement();
    Statement otherwise = new BlockStmt(new ArrayList<>());
    
    if (match(ELSE)) {
      otherwise = statement();
    }
    return new IfStmt(condition, then, otherwise);
  }
  private Expr expression() {
    return assignment();
  }
  private Expr assignment() {
    Expr expr = or();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Var) {
        Token name = ((Var)expr).name;
        return new Assign(name, value);
      }

      error(equals, "Invalid assignment target."); // [no-throw]
    }

    return expr;
  }
  private Expr or() {
    Expr expr = and();

    if (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Logical(expr, operator, right);
    }

    return expr;
    
  }
  private Expr and() {
    Expr expr = equality();

    if (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Logical(expr, operator, right);
    }

    return expr;
  }
  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Binary(expr, operator, right);
    }

    return expr;
  }
  private Expr comparison() {
    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Binary(expr, operator, right);
    }

    return expr;
  }
  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Binary(expr, operator, right);
    }

    return expr;
  }
  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Binary(expr, operator, right);
    }

    return expr;
  }
  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Unary(operator, right);
    }

    return primary();
  }
  private Expr primary() {
    if (match(FALSE)) return new Literal(false);
    if (match(TRUE)) return new Literal(true);
    if (match(NIL)) return new Literal(null);

    if (match(NUMBER, STRING)) {
      return new Literal(previous().literal);
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Grouping(expr);
    }

    return callable();
  }
  private Expr callable() {
    if (match(IDENTIFIER)) {
      Token identifier = previous();
      Expr expr = new Var(identifier);

      while (match(LEFT_PAREN)) 
        expr = new Func(identifier, expr, series(RIGHT_PAREN, (_GetExpression)() -> expression()));
      return expr;
    }

    // Better: Throw an error, then catch and rethrow at expression() and funcDecl()
    // with different error messages.
    throw error(peek(), "Expect identifier.");
  }

  private <T extends Expr> Series<T> series(TokenType closingType, _GetExpression<T> getExpression) {
    List<T> args = new ArrayList<>();
    if (!check(closingType)) {
      args.add(getExpression.call());
      while (match(TokenType.COMMA)) {
        args.add(getExpression.call());
      }
    }
    consume(closingType, String.format("Expect %s.", closingType));
    return new Series<T>(args);
  }
  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }
  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }
  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }
  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }
  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }
  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }
  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}

interface _GetExpression<T extends Expr> {
  T call();
}

/* 
Order of operations:

  statement
  -> varDecl
   | funcDecl
   | statement;

  statement 
   -> print
   | block
   | while
   | for
   | if
   | expression;
    
  expression
   -> assignment
   | logical
   | equality 
   | comparison
   | addsub
   | multdiv
   | unary
   | primary;

  primary
   -> nil false true
   | number
   | ???
   | grouping;

  ???
   -> funcCall
   | identifier;
*/
