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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.org.apache.xalan.internal.xsltc.util.IntegerArray;

import llvmast.LlvmAlloca;
import llvmast.LlvmArray;
import llvmast.LlvmCall;
import llvmast.LlvmCloseDefinition;
import llvmast.LlvmConstantDeclaration;
import llvmast.LlvmDefine;
import llvmast.LlvmExternalDeclaration;
import llvmast.LlvmGetElementPointer;
import llvmast.LlvmInstruction;
import llvmast.LlvmIntegerLiteral;
import llvmast.LlvmLabel;
import llvmast.LlvmLabelValue;
import llvmast.LlvmLoad;
import llvmast.LlvmNamedValue;
import llvmast.LlvmPlus;
import llvmast.LlvmPointer;
import llvmast.LlvmPrimitiveType;
import llvmast.LlvmRegister;
import llvmast.LlvmRet;
import llvmast.LlvmStore;
import llvmast.LlvmStructure;
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
	public LlvmValue visit(ClassDeclSimple n){
		System.out.println("ENTER NODE - Class Declaration Simple");
		
		List<LlvmType> typeList = new LinkedList<LlvmType>();
		
		util.List<VarDecl> vars = n.varList;
		if(vars.head != null){
			typeList.add(checkType(vars.head));
			while(vars.tail != null){
				vars = vars.tail;
				typeList.add(checkType(vars.head));
			}
		}
		
		LlvmStructure newClass = new LlvmStructure(typeList);
		
		return null;
	}
	
	public LlvmType checkType(VarDecl v){
		if(v.type.equals(IntegerArray.class)){
			return new LlvmPointer(LlvmPrimitiveType.I32);
		}else if(v.type.equals(Identifier.class)){
			return LlvmPrimitiveType.I32;
		}
		return LlvmPrimitiveType.I32;
	}

	public LlvmValue visit(ClassDeclExtends n){
		System.out.println("ENTER NODE - Class Declaration Extends");
		return null;
	}

	public LlvmValue visit(VarDecl n) {
		System.out.println("ENTER NODE - Variable Declaration - var: " + n.name.toString());
		
		return n.type.accept(this);
	}

	public LlvmValue visit(MethodDecl n){
		System.out.println("ENTER NODE - Method Declaration");
		return null;
	}

	public LlvmValue visit(Formal n){
		System.out.println("ENTER NODE - Formal");
		return null;
	}

	public LlvmValue visit(IntArrayType n){
		System.out.println("ENTER NODE - Integer Array Type");
		return null;
	}

	public LlvmValue visit(BooleanType n){
		System.out.println("ENTER NODE - Boolean Type");
		return null;
	}

	public LlvmValue visit(IntegerType n){
		System.out.println("ENTER NODE - Integer Type");
		return null;
	}

	public LlvmValue visit(IdentifierType n){
		System.out.println("ENTER NODE - Identifier Type");
		return null;
	}

	public LlvmValue visit(Block n){
		System.out.println("ENTER NODE - Block");
		return null;
	}

	public LlvmValue visit(If n){
		System.out.println("ENTER NODE - If");
		return null;
	}

	public LlvmValue visit(While n){
		System.out.println("ENTER NODE - While");
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

	public LlvmValue visit(And n){
		System.out.println("ENTER NODE - And");
		return null;
	}

	public LlvmValue visit(LessThan n){
		System.out.println("ENTER NODE - Less Than");
		return null;
	}

	public LlvmValue visit(Equal n){
		System.out.println("ENTER NODE - Equal");
		return null;
	}

	public LlvmValue visit(Minus n){
		System.out.println("ENTER NODE - Minus");
		return null;
	}

	public LlvmValue visit(Times n){
		System.out.println("ENTER NODE - Times");
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

	public LlvmValue visit(True n){
		System.out.println("ENTER NODE - True");
		return null;
	}

	public LlvmValue visit(False n){
		System.out.println("ENTER NODE - False");
		return null;
	}

	public LlvmValue visit(IdentifierExp n){
		System.out.println("ENTER NODE - Identifier Exp");
		return null;
	}

	public LlvmValue visit(This n){
		System.out.println("ENTER NODE - This");
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
		return null;
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
	ClassNode (String nameClass, LlvmStructure classType, List<LlvmValue> varList){
	}
}

class MethodNode {
}




