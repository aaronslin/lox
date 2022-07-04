package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  private final List<Token> tokens;
  private int currentToken = 0;

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
  private Statement declaration() {
    try {
      if (check(VAR)) return varDeclaration();
      if (match(FUN)) return funcDeclaration();
      if (check(CLASS)) return classDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }
  private Statement varDeclaration() {
    Token varToken = consume(VAR, "Expect keyword 'var'.");
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = new Empty();
    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new VarStmt(name, initializer, varToken);
  }
  private Statement funcDeclaration() {
    List<Statement> statements = new ArrayList<>();

    Token identifier = consume(IDENTIFIER, "Expect function name.");
    consume(LEFT_PAREN, String.format("Expect '(' after function name '%s'.", identifier));
    _GetExpression getParameter = () -> parameter();
    Series<Var> parameters = series(RIGHT_PAREN, getParameter);
    Token blockToken = consume(LEFT_BRACE, "Expect '{' after function header.");
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }
    consume(RIGHT_BRACE, String.format("Expect '}' after function body for '%s'.", identifier));

    return new FuncStmt(identifier, parameters, new BlockStmt(statements, blockToken), identifier);
  }
  private Statement classDeclaration() {
    List<VarStmt> properties = new ArrayList<>();
    List<FuncStmt> methods = new ArrayList<>();

    Token classToken = consume(CLASS, "Expect keyword 'class'.");
    Token className = consume(IDENTIFIER, "Expect identifier for class name.");
    // (alin) this doesn't support extension yet.
    consume(LEFT_BRACE, "Expect '{' after class name.");
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      // (alin) for now, body only supports methods and properties. Disallow nested classes
      if (check(VAR)) {
        properties.add((VarStmt) varDeclaration());
      } else {
        // Methods do not require the FUN keyword.
        methods.add((FuncStmt) funcDeclaration());
      }
    }
    consume(RIGHT_BRACE, "Expect '}' after class definition.");
    // (alin) not sure if this suffices.
    // (alin) this doesn't support constructors.
    return new ClassStmt(className, properties, methods, classToken);
  }
  private Var parameter() {
    if (match(IDENTIFIER)) {
      return new Var(previous());
    }
    throw error(peek(), "Expect identifier.");
  }
  private Statement statement() {
    if (check(RETURN)) return returnStatement();
    if (check(PRINT)) return printStatement();
    if (match(LEFT_BRACE)) return new BlockStmt(block(), previous());
    if (check(IF)) return conditional();
    if (check(WHILE)) return whileLoop();
    if (check(FOR)) return forLoop();

    return expressionStatement();
  }
  private Statement returnStatement() {
    Token returnToken = consume(RETURN, "Expect 'return' keyword.");
    if (match(SEMICOLON)) return new ReturnStmt(new Empty(), returnToken);

    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after return statement.");
    return new ReturnStmt(expr, returnToken);
  }
  private Statement printStatement() {
    Token printToken = consume(PRINT, "Expect 'print' keyword.");
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new PrintStmt(value, printToken);
  }
  private Statement expressionStatement() {
    Token firstToken = peek();
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new ExprStmt(expr, firstToken);
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
    Token forToken = consume(FOR, "Expect 'for' keyword.");
    consume(LEFT_PAREN, "Expect '(' after 'for'.");
    Statement initializer = varDeclaration();
    Expr condition = expression();
    Token iteratorToken = consume(SEMICOLON, "Expect ';' after 'for' condition.");
    Expr iterator = assignment();
    consume(RIGHT_PAREN, "Expect ')' after 'for'.");
    Statement body = statement();
    return new ForStmt(initializer, condition, new ExprStmt(iterator, iteratorToken), body, forToken);
  }
  private Statement whileLoop() {
    Token whileToken = consume(FOR, "Expect 'while' keyword.");
    Expr condition = primary();
    Statement statement = statement();
    return new WhileStmt(condition, statement, whileToken);
  }
  private Statement conditional() {
    Token ifToken = consume(IF, "Expect 'if' keyword.");
    Expr condition = primary();
    Statement then = statement();
    Statement otherwise = new BlockStmt(new ArrayList<>(), ifToken);
    
    if (match(ELSE)) {
      otherwise = statement();
    }
    return new IfStmt(condition, then, otherwise, ifToken);
  }
  private Expr expression() {
    return assignment();
  }
  private Expr assignment() {
    Expr expr = or();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();
      return new Assign(expr, equals, value);
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

    return callableOrFieldable();
  }

  private Expr callableOrFieldable() {
    Token token = peek();
    Expr expr = identifier();

    while (true) {
      if (match(DOT)) {
        token = consume(IDENTIFIER, "Expect property name.");
        expr = new Property(expr, token);
      } else if (match(LEFT_PAREN)) {
        expr = new Call(
          token, 
          expr, 
          series(RIGHT_PAREN, (_GetExpression)() -> expression())
        );
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr identifier() {
    if (match(THIS)) return new This(previous());
    if (match(IDENTIFIER)) return new Var(previous());
    return primary();
  }

  private Expr primary() {
    if (match(FALSE)) return new Literal(false);
    if (match(TRUE)) return new Literal(true);
    if (match(NIL)) return new Literal(null);
    if (match(THIS)) return new This(previous());

    if (match(NUMBER, STRING)) {
      return new Literal(previous().literal);
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Grouping(expr);
    }

    // Better: Throw an error, then catch and rethrow at expression() and funcDecl()
    // with different error messages.
    throw error(peek(), "Unable to parse token.");
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
    if (!isAtEnd()) currentToken++;
    return previous();
  }
  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(currentToken);
  }

  private Token previous() {
    return tokens.get(currentToken - 1);
  }
  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError(message);
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
