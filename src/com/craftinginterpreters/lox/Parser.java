package com.craftinginterpreters.lox;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Arrays;

import static com.craftinginterpreters.lox.TokenType.*;

/**
  * Grammar so far

  * Program -> Declaration* EOF;
  * Declaration -> VarDecl | Statement
  * VarDecl -> VAR IDENTIFIER ( "=" Expression) ? ";"
  * Statement -> IfStatement | ExpressionStatement | ForStatement | PrintStatement | WhileStatement | Block | Break
  * IfStatement -> "if" "(" Expression ")" Statement ("else" Statement) ?
  * ExpressionStatement -> Expression ";"
  * ForStatement -> "for" "(" varDeclaration | expressionStatement | ";" expression? ";" expression? ")" Statement
  * PrintStatement -> "print" Expression ";"
  * WhileStatement -> "while" "(" Expression ")" Statement
  * Block -> "{" Declaration* "}"
  * Break -> "break" ";"
  * Expression -> Comma expression
  * Comma Expression -> Assignment (, Assignment) *
  * Assignment -> IDENTIFIER "=" Assignment | Ternary Expression
  * Ternary Expression -> Logic_or ( "?" Ternary ":" Ternary) *, right associative
  * Logic_or -> Logic_and ( "or" Logic_and)*, left associative
  * Logic_and -> Equality ( "and" Equality) *, left associative
  * Equality -> Comparison ( "==" | "!=" Comparison ) *, left associative
  * Comparison -> Addition ( "!=" | ">" | ">=" | "<=" Addition) *, left associative
  * Addition -> Multiplication ( "+" | "-" Multiplication) *, left associative
  * Multiplication -> Unary ( "*" | "/" Unary) *, left associative
  * Unary -> ("!" | "-") Unary | Primary
  * Primary -> NUMBER | STRING | "TRUE" | "FALSE" | "NIL" | "(" Expression ")" | IDENTIFIER
**/
class Parser {

  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private boolean loop = false;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Stmt> parse() {

    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }

  Expr parseExpression() {
    try {
      Expr expr = expression();
      return expr;
    }
    catch(ParseError error) {
      return null;
    }
  }

  private Stmt declaration() {
    try {
      if(match(VAR)) return varDeclaration();

      return statement();
    }
    catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt varDeclaration() {
    Token name = consume(IDENTIFIER, "Expected variable name.");

    Expr initializer = null;
    if(match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after value.");

    return new Stmt.Var(name, initializer);
  }

  private Stmt statement() {
    if(match(BREAK)) return breakStatement();
    if(match(FOR)) return forStatement();
    if(match(IF)) return ifStatement();
    if(match(PRINT)) return printStatement();
    if(match(WHILE)) return whileStatement();
    if(match(LEFT_BRACE)) return new Stmt.Block(block());

    return expressionStatement();
  }

  private Stmt breakStatement() {

    Token operator = previous();
    Stmt stmt = new Stmt.Break(operator);
    consume(SEMICOLON, "Expect ';' after break");

    if(!loop) {
      error(operator, "Illegal break statement");
    }

    return stmt;

  }

  private Stmt forStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'for'");
    Stmt initializer;

    if(match(SEMICOLON)) {
      initializer = null;
    }
    else if(match(VAR)) {
      initializer = varDeclaration();
    }
    else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if(!check(SEMICOLON)) {
      condition = expression();
    }

    consume(SEMICOLON, "Expect ';' after loop condition");

    Expr increment = null;
    if(!check(RIGHT_PAREN)) {
      increment = expression();
    }

    consume(RIGHT_PAREN, "Expect ')' after for clauses");

    boolean previous = loop;
    loop = true;
    Stmt body = statement();
    loop = previous;

    if(increment != null){
      body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
    }

    if(condition == null) {
      condition = new Expr.Literal(true);
    }

    body = new Stmt.While(condition, body);

    if(initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    return body;
  }

  private Stmt ifStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'if'");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after expression");

    Stmt thenBranch = statement();
    Stmt elseBranch = null;

    if(match(ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt printStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(expr);
  }

  private Stmt whileStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'while'" );
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect '(' after expression");

    boolean previous = loop;
    loop = true;
    Stmt body = statement();
    loop = previous;

    return new Stmt.While(condition, body);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<Stmt>();
    while(!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Expr expression() {
    return comma();
  }

  //comma support added by @Anunay
  private Expr comma() {
    Expr expr = assignment();

    while(match(COMMA)) {
      Token operator = previous();
      Expr right = assignment();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr assignment() {
    Expr expr = ternary();

    if(match(EQUAL)) {

      Token equals = previous();
      Expr value = assignment();

      if(expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable) expr).name;
        return new Expr.Assign(name, value);
      }

      error(equals, "Invalid assignment target.");
    }

    return expr;
  }

  //ternary support added by @Anunay
  private Expr ternary() {

    Expr expr = or();

    Token loperator = null, roperator = null;

    while(match(QUESTION)) {
      loperator = previous();
      Expr middle = ternary();
      consume(COLON, "Expect ':' after expression.");
      roperator = previous();
      Expr right = ternary();

      expr = new Expr.Ternary(expr, loperator, middle, roperator, expr);
    }

    return expr;
  }

  private Expr or() {
    Expr expr = and();

    while(match(OR)) {
      Token operator = previous();
      Expr right = and();

      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while(match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    Expr expr = addition();
    while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = addition();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr addition() {
    Expr expr = multiplication();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = multiplication();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr multiplication() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if(match(MINUS, BANG)) {
      Token operator = previous();
      Expr right = unary();
      Expr expr = new Expr.Unary(operator, right);

      return expr;
    }

    return primary();
  }

  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if(match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    //report binary operand starting at the beginning
    if(match(BANG_EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL,
            LESS, LESS_EQUAL, MINUS, PLUS, SLASH, STAR)) {

      Token token = previous();
      Lox.error(token.line, "Error at '" + token.lexeme + "', Binary Operator not expected at the beginning of the expression");
      return expression();
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression.");
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

  private boolean check(TokenType tokenType) {
    if (isAtEnd()) return false;
    return peek().type == tokenType;
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
