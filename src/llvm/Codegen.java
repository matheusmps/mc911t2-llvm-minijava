/*****************************************************
Esta classe Codegen é a responsável por emitir LLVM-IR. 
Ela possui o mesmo método 'visit' sobrecarregado de
acordo com o tipo do parâmetro. Se o parâmentro for
do tipo 'While', o 'visit' emitirá código LLVM-IR que 
representa este comportamento. 
Alguns métodos 'visit' já estão prontos e, por isso,
a compilação do código abaixo já é possível.

class a{
    public static void main(String[] args){
    	System.out.println(1+2);
    }
}

O pacote 'llvmast' possui estruturas simples 
que auxiliam a geração de código em LLVM-IR. Quase todas 
as classes estão prontas; apenas as seguintes precisam ser 
implementadas: 

// llvmasm/LlvmBranch.java
// llvmasm/LlvmIcmp.java
// llvmasm/LlvmMinus.java
// llvmasm/LlvmTimes.java


Todas as assinaturas de métodos e construtores 
necessárias já estão lá. 


Observem todos os métodos e classes já implementados
e o manual do LLVM-IR (http://llvm.org/docs/LangRef.html) 
como guia no desenvolvimento deste projeto. 

****************************************************/
package llvm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import llvmast.LlvmAlloca;
import llvmast.LlvmAnd;
import llvmast.LlvmArray;
import llvmast.LlvmBool;
import llvmast.LlvmBranch;
import llvmast.LlvmCall;
import llvmast.LlvmCloseDefinition;
import llvmast.LlvmConstantDeclaration;
import llvmast.LlvmDefine;
import llvmast.LlvmExternalDeclaration;
import llvmast.LlvmGetElementPointer;
import llvmast.LlvmIcmp;
import llvmast.LlvmInstruction;
import llvmast.LlvmIntegerLiteral;
import llvmast.LlvmLabel;
import llvmast.LlvmLabelValue;
import llvmast.LlvmLoad;
import llvmast.LlvmMinus;
import llvmast.LlvmNamedValue;
import llvmast.LlvmNot;
import llvmast.LlvmPlus;
import llvmast.LlvmPointer;
import llvmast.LlvmPrimitiveType;
import llvmast.LlvmRegister;
import llvmast.LlvmRet;
import llvmast.LlvmStore;
import llvmast.LlvmStructure;
import llvmast.LlvmTimes;
import llvmast.LlvmType;
import llvmast.LlvmValue;
import semant.Env;
import syntaxtree.And;
import syntaxtree.ArrayAssign;
import syntaxtree.ArrayLength;
import syntaxtree.ArrayLookup;
import syntaxtree.Assign;
import syntaxtree.Block;
import syntaxtree.BooleanType;
import syntaxtree.Call;
import syntaxtree.ClassDecl;
import syntaxtree.ClassDeclExtends;
import syntaxtree.ClassDeclSimple;
import syntaxtree.Equal;
import syntaxtree.False;
import syntaxtree.Formal;
import syntaxtree.Identifier;
import syntaxtree.IdentifierExp;
import syntaxtree.IdentifierType;
import syntaxtree.If;
import syntaxtree.IntArrayType;
import syntaxtree.IntegerLiteral;
import syntaxtree.IntegerType;
import syntaxtree.LessThan;
import syntaxtree.MainClass;
import syntaxtree.MethodDecl;
import syntaxtree.Minus;
import syntaxtree.NewArray;
import syntaxtree.NewObject;
import syntaxtree.Not;
import syntaxtree.Plus;
import syntaxtree.Print;
import syntaxtree.Program;
import syntaxtree.Statement;
import syntaxtree.This;
import syntaxtree.Times;
import syntaxtree.True;
import syntaxtree.VarDecl;
import syntaxtree.VisitorAdapter;
import syntaxtree.While;

public class Codegen extends VisitorAdapter{
	private List<LlvmInstruction> assembler;
	private Codegen codeGenerator;

  	private SymTab symTab;
	private ClassNode classEnv; 	// Aponta para a classe atualmente em uso em symTab
	private MethodNode methodEnv; 	// Aponta para a metodo atualmente em uso em symTab


	public Codegen(){
		assembler = new LinkedList<LlvmInstruction>();
	}

	// Método de entrada do Codegen
	public String translate(Program p, Env env){	
		codeGenerator = new Codegen();
		
		// Preenchendo a Tabela de Símbolos
		// Quem quiser usar 'env', apenas comente essa linha
		// codeGenerator.symTab.FillTabSymbol(p);
		
		// Formato da String para o System.out.printlnijava "%d\n"
		codeGenerator.assembler.add(new LlvmConstantDeclaration("@.formatting.string", "private constant [4 x i8] c\"%d\\0A\\00\""));	

		// NOTA: sempre que X.accept(Y), então Y.visit(X);
		// NOTA: Logo, o comando abaixo irá chamar codeGenerator.visit(Program), linha 75
		p.accept(codeGenerator);

		// Link do printf
		List<LlvmType> pts = new LinkedList<LlvmType>();
		pts.add(new LlvmPointer(LlvmPrimitiveType.I8));
		pts.add(LlvmPrimitiveType.DOTDOTDOT);
		codeGenerator.assembler.add(new LlvmExternalDeclaration("@printf", LlvmPrimitiveType.I32, pts)); 
		List<LlvmType> mallocpts = new LinkedList<LlvmType>();
		mallocpts.add(LlvmPrimitiveType.I32);
		codeGenerator.assembler.add(new LlvmExternalDeclaration("@malloc", new LlvmPointer(LlvmPrimitiveType.I8),mallocpts)); 


		String r = new String();
		for(LlvmInstruction instr : codeGenerator.assembler)
			r += instr+"\n";
		return r;
	}

	public LlvmValue visit(Program n){
		System.out.println("ENTER NODE - Program");

		n.mainClass.accept(this);

		for (util.List<ClassDecl> c = n.classList; c != null; c = c.tail)
			c.head.accept(this);

		return null;
	}

	public LlvmValue visit(MainClass n){
		System.out.println("ENTER NODE - Main Class");
		
		// definicao do main 
		assembler.add(new LlvmDefine("@main", LlvmPrimitiveType.I32, new LinkedList<LlvmValue>()));
		assembler.add(new LlvmLabel(new LlvmLabelValue("entry")));
		LlvmRegister R1 = new LlvmRegister(new LlvmPointer(LlvmPrimitiveType.I32));
		assembler.add(new LlvmAlloca(R1, LlvmPrimitiveType.I32, new LinkedList<LlvmValue>()));
		assembler.add(new LlvmStore(new LlvmIntegerLiteral(0), R1));

		// Statement é uma classe abstrata
		// Portanto, o accept chamado é da classe que implementa Statement, por exemplo,  a classe "Print". 
		n.stm.accept(this);  

		// Final do Main
		LlvmRegister R2 = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmLoad(R2,R1));
		assembler.add(new LlvmRet(R2));
		assembler.add(new LlvmCloseDefinition());
		return null;
	}
	
	public LlvmValue visit(Plus n){
		System.out.println("ENTER NODE - Plus");

		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmPlus(lhs,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
	}
	
	public LlvmValue visit(Print n){
		System.out.println("ENTER NODE - Print");

		LlvmValue v =  n.exp.accept(this);

		// getelementptr:
		LlvmRegister lhs = new LlvmRegister(new LlvmPointer(LlvmPrimitiveType.I8));
		LlvmRegister src = new LlvmNamedValue("@.formatting.string",new LlvmPointer(new LlvmArray(4,LlvmPrimitiveType.I8)));
		List<LlvmValue> offsets = new LinkedList<LlvmValue>();
		offsets.add(new LlvmIntegerLiteral(0));
		offsets.add(new LlvmIntegerLiteral(0));
		List<LlvmType> pts = new LinkedList<LlvmType>();
		pts.add(new LlvmPointer(LlvmPrimitiveType.I8));
		List<LlvmValue> args = new LinkedList<LlvmValue>();
		args.add(lhs);
		args.add(v);
		assembler.add(new LlvmGetElementPointer(lhs,src,offsets));

		pts = new LinkedList<LlvmType>();
		pts.add(new LlvmPointer(LlvmPrimitiveType.I8));
		pts.add(LlvmPrimitiveType.DOTDOTDOT);
		
		// printf:
		assembler.add(new LlvmCall(new LlvmRegister(LlvmPrimitiveType.I32),
				LlvmPrimitiveType.I32,
				pts,				 
				"@printf",
				args
				));
		return null;
	}
	
	public LlvmValue visit(IntegerLiteral n){
		System.out.println("ENTER NODE - Integer Literal");
		return new LlvmIntegerLiteral(n.value);
	};

	// Todos os visit's que devem ser implementados
	
	// CLASS OPERATIONS
	
	public LlvmValue visit(ClassDeclSimple n){
		System.out.println("ENTER NODE - Class Declaration Simple");

		// Passa por todos os atributos e cria a structure da class
		List<LlvmType> typeList = new LinkedList<LlvmType>();
		List<LlvmValue> varList = new LinkedList<LlvmValue>();
		
		LlvmValue v;
		util.List<VarDecl> vars = n.varList;
		for(; vars != null; vars = vars.tail){
			v = vars.head.accept(this);
			varList.add(v);
			typeList.add(v.type);
		}
		
		LlvmStructure newClass = new LlvmStructure(typeList);
		assembler.add(new LlvmConstantDeclaration("%class." + n.name.toString(), "type " + newClass.toString()));

		//atualizacao do classEnv
		classEnv = new ClassNode(n.name.toString(), newClass, varList);
		
		// Define um construtor
//		List<LlvmValue> param = new LinkedList<LlvmValue>();
//		param.add(new LlvmNamedValue("%this", new LlvmPointer(new LlvmPrimitiveType(n.name.s))));
//		assembler.add(new LlvmDefine("@_"+n.name.toString()+"_"+n.name.toString(), LlvmPrimitiveType.VOID, param));
//		assembler.add(new LlvmLabel(new LlvmLabelValue("entry")));
//		assembler.add(new LlvmRet(new LlvmNamedValue("", LlvmPrimitiveType.VOID)));
//		assembler.add(new LlvmCloseDefinition());
		
		// Define os métodos
		util.List<MethodDecl> methods = n.methodList;
		for(; methods != null; methods = methods.tail){
			methods.head.accept(this);
		}

		return null;
	}

	public LlvmValue visit(ClassDeclExtends n){
		System.out.println("ENTER NODE - Class Declaration Extends");
		return null;
	}

	public LlvmValue visit(VarDecl n) {
		System.out.println("ENTER NODE - Variable Declaration - var: " + n.name.toString());
		
		LlvmValue tp = n.type.accept(this);
		return new LlvmNamedValue(n.name.toString(), tp.type);
	}

	public LlvmValue visit(MethodDecl n){
		System.out.println("ENTER NODE - Method Declaration");

		// Lista de variaveis locais
		Map<String, LlvmValue> listLocals = new HashMap<String, LlvmValue>();
		util.List<VarDecl> locals = n.locals;
		for( ;locals != null; locals = locals.tail){
			LlvmValue v = locals.head.accept(this);
			listLocals.put(locals.head.name.toString(), v);
		}
			
		// Lista de parametros
		List<LlvmValue> listFormals = new LinkedList<LlvmValue>();
		listFormals.add(new LlvmNamedValue("%this", new LlvmPointer(new LlvmPrimitiveType(classEnv.name))));
		
		util.List<Formal> formals = n.formals;
		for( ;formals != null; formals = formals.tail){
			LlvmValue c = formals.head.accept(this);
			listFormals.add(c);
			listLocals.put(formals.head.name.toString(), c);
		}
			
		// Atualiza methodEnv
		methodEnv = new MethodNode(n.name.toString(), listLocals);
		
		// TODO: no caso em que retorna uma classe deve ser um ponteiro
		assembler.add(new LlvmDefine("@__" + classEnv.name + "_" + n.name, n.returnType.accept(this).type, listFormals));
		assembler.add(new LlvmLabel(new LlvmLabelValue("entry")));
		
		// Aloca e armazena cada um dos parametros da funcao
		LlvmRegister r;
		for(int i=1; i<listFormals.size(); i++){
			r = new LlvmRegister(listFormals.get(i).type);
			assembler.add(new LlvmAlloca(r, r.type, new LinkedList<LlvmValue>()));
			assembler.add(new LlvmStore(listFormals.get(i), new LlvmNamedValue(r.name, new LlvmPointer(r.type))));
		}
		
		// percorre as intrucoes do metodo
		util.List<Statement> stm = n.body;
		for(; stm != null; stm = stm.tail){
			stm.head.accept(this);
		}

		LlvmValue v = n.returnExp.accept(this);
		assembler.add(new LlvmRet(v));
		assembler.add(new LlvmCloseDefinition());
		
		//?? methodEnv = null
		
		return null;
	}

	// TIPOS

	// parametros de funcoes
	public LlvmValue visit(Formal n){
		System.out.println("ENTER NODE - Formal");
		return new LlvmNamedValue("%" + n.name, n.type.accept(this).type);
	}

	public LlvmValue visit(IntArrayType n){
		System.out.println("ENTER NODE - Integer Array Type");
		return new LlvmNamedValue("intArray", new LlvmPointer(LlvmPrimitiveType.I32));
	}

	public LlvmValue visit(BooleanType n){
		System.out.println("ENTER NODE - Boolean Type");
		return new LlvmNamedValue("boolean", LlvmPrimitiveType.I1);
	}

	public LlvmValue visit(IntegerType n){
		System.out.println("ENTER NODE - Integer Type");
		return new LlvmNamedValue("int", LlvmPrimitiveType.I32);
	}

	public LlvmValue visit(IdentifierType n){
		System.out.println("ENTER NODE - Identifier Type");
		return new LlvmNamedValue("identifier", new LlvmPointer(new LlvmPrimitiveType(n.name.toString())));
	}
	
	// this = acesso a classe atual
	public LlvmValue visit(This n){
		System.out.println("ENTER NODE - This");
		return new LlvmNamedValue("%this", new LlvmPointer(classEnv));
	}
	
	// OPERATIONS
	
	public LlvmValue visit(And n){
		System.out.println("ENTER NODE - And");
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I1);
		assembler.add(new LlvmAnd(lhs,LlvmPrimitiveType.I1,v1,v2));
		return lhs;
	}

	public LlvmValue visit(LessThan n){
		System.out.println("ENTER NODE - Less Than");
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		LlvmIcmp cmp = new LlvmIcmp(lhs,2,LlvmPrimitiveType.I32,v1,v2);
		assembler.add(cmp);
		return lhs;
	}

	public LlvmValue visit(Equal n){
		System.out.println("ENTER NODE - Equal");
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmIcmp(lhs,1,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
	}

	public LlvmValue visit(Minus n){
		System.out.println("ENTER NODE - Minus");
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmMinus(lhs,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
	}

	public LlvmValue visit(Times n){
		System.out.println("ENTER NODE - Times");
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmTimes(lhs,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
	}
	
	public LlvmValue visit(True n){
		System.out.println("ENTER NODE - True");
		return (new LlvmBool(LlvmBool.TRUE));
	}

	public LlvmValue visit(False n){
		System.out.println("ENTER NODE - False");
		return (new LlvmBool(LlvmBool.FALSE));
	}
	
	public LlvmValue visit(If n){
		System.out.println("ENTER NODE - If");
		
		LlvmValue cond = n.condition.accept(this);
		LlvmLabelValue t = new LlvmLabelValue("ifTrue"+n.line);
		LlvmLabelValue f = new LlvmLabelValue("ifFalse"+n.line);
		LlvmLabelValue e = new LlvmLabelValue("ifEnd"+n.line);
		
		if(n.elseClause != null)
			assembler.add(new LlvmBranch(cond,t,f));
		else
			assembler.add(new LlvmBranch(cond,t,e));
		
		assembler.add(new LlvmLabel(t));
		n.thenClause.accept(this);
		assembler.add(new LlvmBranch(e));
		if(n.elseClause != null){
			assembler.add(new LlvmLabel(f));
			n.elseClause.accept(this);
		}
		assembler.add(new LlvmLabel(e));
		
		return cond;
	}
	
	public LlvmValue visit(While n){
		System.out.println("ENTER NODE - While");
		
		LlvmValue cond = n.condition.accept(this);
		
		LlvmLabelValue clean = null;
		LlvmLabelValue w = new LlvmLabelValue("ifWhile" + n.line);
		LlvmLabelValue d = new LlvmLabelValue("ifDo" + n.line);
		LlvmLabelValue e = new LlvmLabelValue("ifEnd" + n.line);
		
		assembler.add(new LlvmBranch(clean, w, clean));
		assembler.add(new LlvmLabel(w));
		assembler.add(new LlvmBranch(cond, d, e));
		assembler.add(new LlvmLabel(d));
		
  		n.body.accept(this);
		assembler.add(new LlvmBranch(clean, w, clean));
		assembler.add(new LlvmLabel(e));
	
		return null;
	}

	
	// TODO
	
	public LlvmValue visit(Block n){
		System.out.println("ENTER NODE - Block");
		
		util.List<Statement> stm = n.body;
		for( ;stm != null; stm = stm.tail){
			stm.head.accept(this);
		}
		
		return null;
	}

	public LlvmValue visit(Assign n){
		System.out.println("ENTER NODE - Assign");
		return null;
	}

	public LlvmValue visit(ArrayAssign n){
		System.out.println("ENTER NODE - Array Sign");
		return null;
	}

	public LlvmValue visit(ArrayLookup n){
		System.out.println("ENTER NODE - Array Lookup");
		return null;
	}

	public LlvmValue visit(ArrayLength n){
		System.out.println("ENTER NODE - Array Lenght");
		return null;
	}

	public LlvmValue visit(Call n){
		System.out.println("ENTER NODE - Call");
		return null;
	}

	public LlvmValue visit(IdentifierExp n){
		System.out.println("ENTER NODE - Identifier Exp");
		return null;
	}

	public LlvmValue visit(NewArray n){
		System.out.println("ENTER NODE - New Array");
		return null;
	}

	public LlvmValue visit(NewObject n){
		System.out.println("ENTER NODE - New Object");
		return null;
	}

	public LlvmValue visit(Not n){
		System.out.println("ENTER NODE - Not");
		LlvmValue v1 = n.exp.accept(this);
		LlvmValue v2 = new LlvmBool(LlvmBool.TRUE);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I1);
		assembler.add(new LlvmNot(lhs,LlvmPrimitiveType.I1,v1,v2));
		return lhs;
	}

	public LlvmValue visit(Identifier n){
		System.out.println("ENTER NODE - Identifier");
		return null;
	}
}


/**********************************************************************************/
/* === Tabela de Símbolos ==== 
 * 
 *
 */
/**********************************************************************************/

class SymTab extends VisitorAdapter{
    public Map<String, ClassNode> classes;     
    private ClassNode classEnv;    //aponta para a classe em uso

    public LlvmValue FillTabSymbol(Program n){
		n.accept(this);
		return null;
	}
	public LlvmValue visit(Program n){
		n.mainClass.accept(this);

		for (util.List<ClassDecl> c = n.classList; c != null; c = c.tail)
			c.head.accept(this);

		return null;
	}

	public LlvmValue visit(MainClass n){
		classes.put(n.className.s, new ClassNode(n.className.s, null, null));
		return null;
	}

	public LlvmValue visit(ClassDeclSimple n){
		List<LlvmType> typeList = null;
		// Constroi TypeList com os tipos das variáveis da Classe (vai formar a Struct da classe)
		
		List<LlvmValue> varList = null;
		// Constroi VarList com as Variáveis da Classe

		classes.put(n.name.s, new ClassNode(n.name.s, 
											new LlvmStructure(typeList), 
											varList)
					);
			// Percorre n.methodList visitando cada método
		return null;
	}

	public LlvmValue visit(ClassDeclExtends n){return null;}
	public LlvmValue visit(VarDecl n){return null;}
	public LlvmValue visit(Formal n){return null;}
	public LlvmValue visit(MethodDecl n){return null;}
	public LlvmValue visit(IdentifierType n){return null;}
	public LlvmValue visit(IntArrayType n){return null;}
	public LlvmValue visit(BooleanType n){return null;}
	public LlvmValue visit(IntegerType n){return null;}
}

class ClassNode extends LlvmType {
	public String name;
	public LlvmStructure classType;
	public List<LlvmValue> varList;
	
	ClassNode (String nameClass, LlvmStructure classType, List<LlvmValue> varList){
		this.name = nameClass;
		this.classType = classType;
		this.varList = varList;
	}
	
	public String toString(){ return name; }
}

class MethodNode {
	public String name;
	public Map<String, LlvmValue> locals;
	
	public MethodNode(String name, Map<String, LlvmValue> locals) {
		this.name = name;
		this.locals = locals;
	}
	
	public String toString(){ return name; }
}




