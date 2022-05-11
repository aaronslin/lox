package com.craftinginterpreters.lox;

import com.google.common.collect.ImmutableList;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Parser {
  final static List<TokenType> LITERAL_TYPES = ImmutableList.of(
    TokenType.NUMBER,
    TokenType.STRING,
    TokenType.IDENTIFIER,
    TokenType.TRUE,
    TokenType.FALSE,
    TokenType.NIL
  );

  final static List<TokenType> UNARY_TYPES = ImmutableList.of(
    TokenType.BANG,
    TokenType.MINUS
  );

  // Order of operations
  final static List<List<TokenType>> BINARY_TYPES = ImmutableList.of(
    ImmutableList.of(
      TokenType.SLASH,
      TokenType.STAR
    ),
    ImmutableList.of(
      TokenType.PLUS,
      TokenType.MINUS
    ),
    ImmutableList.of(
      TokenType.GREATER_EQUAL,
      TokenType.LESS_EQUAL,
      TokenType.GREATER,
      TokenType.LESS
    ),
    ImmutableList.of(
      TokenType.BANG_EQUAL,
      TokenType.EQUAL_EQUAL
    ),
    ImmutableList.of(
      TokenType.AND,
      TokenType.OR
    )
  );

  final static boolean isDebug = false;

  int index;
  List<Token> tokens;

  Parser(List<Token> tokens) {
    this.index = 0;
    this.tokens = tokens;
  }

  private static void print(List<Lexeme> lexemes) {
    if (isDebug) {
      String caller = Thread.currentThread().getStackTrace()[2].getMethodName();
      System.out.printf("%s:%n", caller);
      for (Lexeme lexeme : lexemes) {
        System.out.printf("|_ %s%n", lexeme);
      }
    }
  }

  private void printCurrent() {
    if (isDebug) {
      String caller = Thread.currentThread().getStackTrace()[2].getMethodName();
      System.out.printf("%s:%n", caller);
      for (int i=0; i<tokens.size(); i++) {
        String open = " ";
        String close = " ";
        if (i == index) {
          open = "[";
          close = "]";
        }
        System.out.println("└─" + open + tokens.get(i) + close);
      }
    }
  }

  private Void until(TokenType terminalType) {
    /* Find the first `terminalType` token from the current `index` (inclusive)
    and set the `index` to the token after it. */
    while (index < tokens.size()) {
      if (tokens.get(index).type == terminalType) {
        index += 1;
        return null;
      }
      index += 1;
    }
    throw new ParserException(String.format("Reached EOF without finding %s token.", terminalType));
  }

  public List<Statement> parseProgram() {
    printCurrent();
    List<Statement> statements = new ArrayList<>();
    
    while (index < tokens.size()) {
      if (tokens.get(index).type == TokenType.EOF) {
        break;
      }
      statements.add(parseBlock());
    }
    return statements;
  }

  private Statement parseBlock() {
    printCurrent();
    if (tokens.get(index).type == TokenType.LEFT_BRACE) {
      int start = index;
      until(TokenType.RIGHT_BRACE);
      Parser parser = new Parser(tokens.subList(start+1, index-1));
      return new BlockStmt(parser.parseProgram());
    }

    return parseLine();
  }

  private Statement parseLine() {
    printCurrent();
    int start = index;
    until(TokenType.SEMICOLON);
    return parseStmt(tokens.subList(start, index-1));
  }

  private static Statement parseStmt(List<Token> tokens) {
    if (tokens.size() > 0 && tokens.get(0).type == TokenType.PRINT) {
      return new PrintStmt(parseExpr(tokens.subList(1, tokens.size())));
    } else if (tokens.size() > 1
            && tokens.get(0).type == TokenType.VAR
            && tokens.get(1).type == TokenType.IDENTIFIER) {
      List<Token> rhs = tokens.subList(2, tokens.size());

      if (rhs.size() == 0) {
        return _parseAssign(tokens.get(1), new Empty());
      } else if (rhs.get(0).type == TokenType.EQUAL) {
        return _parseAssign(tokens.get(1), rhs.subList(1, rhs.size()));
      }
      throw new ParserException(String.format(
        "Assignment must contain an '=' followed by expression, but found %s.", 
        tokens
      ));
    } else if (tokens.size() > 1
            && tokens.get(0).type == TokenType.IDENTIFIER
            && tokens.get(1).type == TokenType.EQUAL) {
      return _parseAssign(tokens.get(0), tokens.subList(2, tokens.size()));
    }
    return new ExprStmt(parseExpr(tokens));
  }

  private static Statement _parseAssign(Token varName, List<Token> tokens) {
    if (tokens.size() == 0) {
      throw new ParserException(String.format("Cannot assign variable '%s' to an empty expression.", varName));
    }
    return _parseAssign(varName, parseExpr(tokens));
  }

  private static Statement _parseAssign(Token varName, Expr expr) {
    return new AssignStmt(
      varName.literal.toString(), 
      new Variable(expr)
    );
  }

  public static Expr parseExpr(List<Token> tokens) {
    /* 
    Assumes:
     * No EOF in input.

    1. Parse literals ("nouns").
    2. For each grouping ("clauses"):
       a. Parse unary operators ("adjectives").
       b. Parse binary operators ("verbs").
    */ 
    if (tokens.size() == 0) {
      return new Empty();
    }

    List<Lexeme> lexemes = new ArrayList<>();
    Stack<List<Lexeme>> exprStack = new Stack<>();
    Stack<Token> openParenStack = new Stack<>();

    for (Token token : tokens) {
      // Match literals and cast them to Exprs
      if (LITERAL_TYPES.contains(token.type)) {
        lexemes.add(new Literal(token));
        continue;
      }

      // Find matching parenthesis
      if (token.type == TokenType.LEFT_PAREN) {
        exprStack.push(lexemes);
        openParenStack.push(token);
        lexemes = new ArrayList<>();
        continue;
      } else if (token.type == TokenType.RIGHT_PAREN) {
        Expr ungroupedExpr = parseGroup(lexemes);
        // case 1: unparsable... just throw the error
        // case 2: parsed
        try {
          Token openParen = openParenStack.pop();
          lexemes = exprStack.pop();
          Expr expr = new Grouping(openParen, ungroupedExpr, token);
          lexemes.add(expr);
        } catch (EmptyStackException e) {
          throw new ParserException(String.format("Unmatched %s.", token.lexeme));
        }
        continue;
      }

      // Ignore everything else
      lexemes.add(token);
    }

    // Check that the stacks are empty. This could be simplified without having openParenStack.
    if (!openParenStack.empty()) {
      Token token = openParenStack.pop();
      throw new ParserException(String.format("Unmatched %s.", token.lexeme));
    } else if (!exprStack.empty()) {
      lexemes = exprStack.pop();
      throw new ParserException(String.format("Extraneous lexemes: %s.", lexemes));
    }

    print(lexemes);
    return parseGroup(lexemes);
  }

  private static Expr parseGroup(List<Lexeme> unparsed) {
    if (unparsed.size() == 0) {
      throw new ParserException("Empty group is not allowed.");
    }
    List<Lexeme> lexemes = unparsed;

    lexemes = _parseUnary(lexemes);
    lexemes = _parseBinary(lexemes);

    if (lexemes.size() > 1) {
      throw new ParserException(String.format(
        "Failed to parse input into a single expression. Parsed %s into %s.",
        unparsed,
        lexemes
      ));
    }
    print(lexemes);
    return (Expr) lexemes.get(0);
  }

  private static List<Lexeme> _parseBinary(List<Lexeme> lexemes) {
    for (List<TokenType> operations : BINARY_TYPES) {
      lexemes = _parseBinary(lexemes, operations);
    }
    return lexemes;
  }

  private static List<Lexeme> _parseBinary(List<Lexeme> lexemes, List<TokenType> operations) {
    /*
    Assumes:
      * Input is alternates between Expr's and operation Token's, starting and ending with Expr's.
      * All grouping and unary operators have been stripped.
    */
    // Assert lexemes are in [Expr Token Expr Token ... Expr] format.
    int violation = IntStream.range(0, lexemes.size())
      .map(i -> (i%2 == 0 ? lexemes.get(i) instanceof Expr : lexemes.get(i) instanceof Token) ? 1 : 0)
      .boxed()
      .collect(Collectors.toList())
      .indexOf(0);

    if (violation == 0) {
      throw new ParserException(String.format(
        "Group must start with an expression, but found %s.", 
        lexemes.get(0)
      ));
    } else if (violation > 0 && violation % 2 == 0) {
      throw new ParserException(String.format(
        "Cannot parse two adjacent tokens: %s %s", 
        lexemes.get(violation-1),
        lexemes.get(violation)
      ));
    } else if (violation > 0 && violation % 2 == 1) {
      throw new ParserException(String.format(
        "Cannot parse two adjacent expressions: %s %s", 
        lexemes.get(violation-1),
        lexemes.get(violation)
      ));
    } else if (lexemes.size() % 2 == 0) {
      throw new ParserException(String.format(
        "Group must end with an expression, but found %s.", 
        lexemes.get(lexemes.size()-1)
      ));
    }

    // Split `lexemes` into exprs and tokens
    List<Expr> exprs = new ArrayList<>();
    List<Token> tokens = new ArrayList<>();
    for (int i=0; i<lexemes.size(); i++) {
      if (i%2 == 0) {
        exprs.add((Expr) lexemes.get(i));
      } else {
        tokens.add((Token) lexemes.get(i));
      }
    }

    List<Lexeme> parsed = new ArrayList<>();
    for (int i=0; i<tokens.size(); i++) {
      if (operations.contains(tokens.get(i).type)) {
        Binary operated = new Binary(exprs.get(i), tokens.get(i), exprs.get(i+1));
        exprs.set(i+1, operated);
      } else {
        parsed.add(exprs.get(i));
        parsed.add(tokens.get(i));
      }
    }
    parsed.add(exprs.get(exprs.size()-1));

    print(parsed);
    return parsed;
  }

  private static List<Lexeme> _parseUnary(List<Lexeme> unparsed) {
    /* 
    Assumes:
     * All parenthesis have been stripped and parsed into Expr's.
     * All literals have been parsed into Expr's.

    Parses:
     * Unary operators on expressions.

    Correctness check:
     * Add `prev` to `parsed`, unless it is consumed by the unary parsing logic.
     * Directly lexeme to `parsed` directly iff `prev` will be set to null.
    */
    List<Lexeme> parsed = new ArrayList<>();
    Expr prev = null;

    // Iterate in reverse order.
    for (int i=unparsed.size()-1; i>=0; i--) {
      Lexeme lexeme = unparsed.get(i);
      if (lexeme instanceof Token) {
        Token token = (Token) lexeme;
        if (UNARY_TYPES.contains(token.type) 
            && prev != null 
            && (i==0 || !(unparsed.get(i-1) instanceof Expr))) {
          Unary expr = new Unary(token, prev);
          prev = expr;
          continue;
        }
        parsed.add(prev);
        prev = null;
        parsed.add(token);
      } else if (lexeme instanceof Expr) {
        parsed.add(prev);
        prev = (Expr) lexeme;
      } else {
        throw new ParserException(String.format("Unrecognized lexeme type: %s", lexeme.getClass().getName()));
      }
    }
    parsed.add(prev);

    Collections.reverse(parsed);
    while (parsed.remove(null));
    print(parsed);
    return parsed;
  }
}

class ParserException extends RuntimeException {
  String code;
  int column;

  public ParserException(String message, String code, int column) {
    super(message);
    this.code = code;
    this.column = column;
  }

  public ParserException(String message) {
    this(message, "", -1);
  }
}

/* 
Ch. 8 Notes:
- Handle execute() Runtime errors
- Variable should just be an Expr
- Lox disallows `if (monday) var beverage = "espresso";` because the scope of beverage is ambiguous
  This is why they chose to create a declaration -> varDeclaration | statement
- Book also categorizes `var a = 1` as a statement (higher precedence than expression)
  but `a=1` as an expression.
- Recursive descent is nice. Essentially, "Parse everything higher precedence first. Then match on some token. 
  If the match misses, then let the lower precedence rules try matching."
- Book creates an Expr.Var(Token) -- note: not Expr.Var(Token, Object). There is a separate
  Stmt.Var(Token, Expr) for variable declarations -- note: not Stmt.Var(Token, Object).
  Finally, there is an Expr.Assign(Token, Expr) for assignments -- note: not Expr.Assign(String, Expr).
- environment.get has an interesting choice between throwing a compile error or runtime error.
  A compiler error would affect recursion and multi-function recursion
  because if f is defined before g, f cannot mention g. Hence runtime error.
- Mutation seems like a big topic (separate from assignment). Rust and Haskell opinionated about this.
- The single lookahead recursive descent parser is different for the two expressions: `a + b` vs. `a = 1`.
  The former "evaluates" everything that comes before the +, so it can do this:
     expression()
     consume(TokenType.PLUS);
     expression()
  However, `a=1` is different because we aren't evaluating a before assigning it.
    * Me: I'm not sure why this makes a difference, given that this lives in the parser, not the interpreter.

  Also assignment is right associative (a = (b = c)) while + is left associative ((a + b) + c).

- The book avoids looping to parse the left hand side of assignment expressions by taking advantage
  of the fact that every valid assignable LHS is also a valid standalone expression. (e.g. `Cat.name;` vs `Cat.name = ...`)
- The parser distinguishes between grouped and ungrouped expressions because `(a) = 3` is invalid.
- Static vs dynamic scope: See this: https://stackoverflow.com/questions/22394089/static-lexical-scoping-vs-dynamic-scoping-pseudocode
  Dynamic scope is determined by the runtime stack. Static scope is defined by the code block structure.
  Apparently static is faster than dynamic?

  My interpreter is written with dynamic scope, while the book uses static scope.
- Another huge implementation difference: assign and declaration are different because of nested environments:
  Declaration always declares in the innermost / current scope.
  Assignment walks the tree and can assign variables in outer scopes!

  If we support implicit variable declaration (e.g. no `var` needed), we have to decide how to handle.
   - Python: in current scope
   - Ruby: outer block if exists, else current scope
   - JS: outer block if exists, else global scope
- Book chooses to call { } a Stmt.Block. I didn't like this name because "block" is vague, but ig it's okay.
  Their code for parsing blocks is so much simpler than I imagined. 
    if(match(LEFT_BRACE)) return new Block(block());
    Block block() {
      while(!isAtEnd && !match(RIGHT_BRACE)) {
        statements.add(declaration());
      }
      throw "Unmatched }";
    }
- try... finally in Java. This way parent scope gets restored even with exceptions!
- also their executeBlock(Stmt, Environment), not executeBlock(Stmt)
- Interesting edge case: 
  var a = 1;
  {
    // I think this will NOT throw RuntimeError because (1) the decl will parse correctly (2) when initializing
    // it will find that `a` exists in outer scope but not current scope (3) then it will assign `1 + 2` to `a` in inner scope.
    var a = a + 2;
    print a;
  }

Ch. 7 Notes:

- Why is equality a different order of operation than comparison?
- "Each rule here only matches expressions at its precedence level or higher. " Hence:


program -> statement* EOF
statement -> { statement* }

statement -> expression ;
statement -> PRINT expression ;
statement -> (var)? IDENT (= expression)?;

expression     → equality ;
equality       → comparison ( ( "!=" | "==" ) comparison )* ;
comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term           → factor ( ( "-" | "+" ) factor )* ;
factor         → unary ( ( "/" | "*" ) unary )* ;
unary          → ( "!" | "-" ) unary
               | primary ;
primary        → NUMBER | STRING | "true" | "false" | "nil"
               | "(" expression ")" ;


The really cool thing is that this grammar can be unpacked very nicely into parsing code!
 * terminal (e.g. NUMBER or "/" ) = consume that token and advance
 * nonterminal (e.g. comparison) = a function call
 * ? or | = if/switch
 * (...)+ or (...)* = for/while

- left vs. right associativity
- concept of synchronization -- when a parser finds an error, it would be nice to continue and 
  report as many errors at once as possible. One can synchronize per-statement. If one statement
  throws an error, ignore everything else on that line and continue parsing on the next line.
- error reporting is surprisingly nontrivial!
*/

