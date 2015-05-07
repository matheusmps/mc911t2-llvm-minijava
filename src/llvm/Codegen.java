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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import llvmast.LlvmAlloca;
import llvmast.LlvmAnd;
import llvmast.LlvmArray;
import llvmast.LlvmBitcast;
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
import llvmast.LlvmMalloc;
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
		methodEnv = new MethodNode();
	}

	// Método de entrada do Codegen
	public String translate(Program p, Env env){	
		codeGenerator = new Codegen();
		
		codeGenerator.symTab = new SymTab(codeGenerator);
		codeGenerator.symTab.FillTabSymbol(p);
		codeGenerator.symTab.FillTabSymbol(p);
		
		for(String key : codeGenerator.symTab.classes.keySet()){
			LlvmStructure struct = codeGenerator.symTab.classes.get(key).classType;
			if (struct == null)
				struct = new LlvmStructure(new ArrayList<LlvmType>());
			codeGenerator.assembler.add(new LlvmConstantDeclaration("%class."+key, "type " + struct));
		}
		
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
		LlvmValue off = new LlvmIntegerLiteral(0);
		offsets.add(off);
		offsets.add(off);
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

	
	// CLASS OPERATIONS

	public LlvmValue visit(ClassDeclSimple n){
		System.out.println("ENTER NODE - ClassDeclSimple");

		classEnv = symTab.classes.get(n.name.s);
		for (util.List<syntaxtree.MethodDecl> var = n.methodList; var!=null; var=var.tail){
			var.head.accept(this);
		}
		classEnv = null;
		return null;
	}

	public LlvmValue visit(ClassDeclExtends n){
		System.out.println("ENTER NODE - ClassDeclExtends");

		classEnv = symTab.classes.get(n.name.s);
		for (util.List<syntaxtree.MethodDecl> var = n.methodList; var!=null; var=var.tail){
			var.head.accept(this);
		}
		classEnv = null;
		return null;
	}

	public LlvmValue visit(VarDecl n){
		System.out.println("ENTER NODE - Var Declaration");
		
		LlvmType type = n.type.accept(this).type;
		if (type instanceof ClassNode)
			type = new LlvmPointer(type);
		LlvmValue lhs = new LlvmRegister("%"+n.name.s,type);
		assembler.add(new LlvmAlloca(lhs, type, null));
		lhs.type = new LlvmPointer(lhs.type);
		return lhs;
	}

	public LlvmValue visit(MethodDecl n){
		System.out.println("ENTER NODE - Method Declaration");
		
		LlvmValue type = n.returnType.accept(this);
		List<LlvmValue> args = new ArrayList<LlvmValue>();

		methodEnv.formals.clear();
		methodEnv.vars.clear();
		
		//this
		args.add(new LlvmNamedValue("%this", new LlvmPointer(classEnv)));
		methodEnv.formalTypes.add(new LlvmPointer(classEnv).toString());
		
		util.List<Formal> vars = n.formals;
		for (; vars != null; vars = vars.tail){
			LlvmValue val = vars.head.accept(this);
			args.add(val);
			methodEnv.formals.put(vars.head.name.toString(), val);
			methodEnv.formalTypes.add(val.type.toString());
		}
		assembler.add(new LlvmDefine("@__"+n.name.s+"_"+classEnv.name, type.type, args));
		assembler.add(new LlvmLabel(new LlvmLabelValue("entry")));
		
		// params
		boolean passed_this = false;
		for (LlvmValue val : args){
			if (passed_this){ // Pula o "this" do vetor
				LlvmType t = val.type;
				if (t instanceof ClassNode)
					t = new LlvmPointer(t);
				LlvmValue formLocal = new LlvmRegister(((LlvmNamedValue)val).name+"_local",new LlvmPointer(t));
				assembler.add(new LlvmAlloca(formLocal, t, null));
				assembler.add(new LlvmStore(val, formLocal));
			}
			passed_this=true;
		}
		
		for (util.List<VarDecl> var = n.locals; var!=null; var=var.tail){
			methodEnv.vars.put(var.head.name.toString(), var.head.accept(this));
		}
		
		for (util.List<Statement> stm = n.body; stm!=null; stm=stm.tail){
			stm.head.accept(this);
		}
		
		// Retorna o tipo adequado
		LlvmValue typeReturnExp = n.returnExp.accept(this);
		if(typeReturnExp.type != type.type){
			LlvmRegister r = new LlvmRegister(type.type);
			assembler.add(new LlvmBitcast(r, typeReturnExp, type.type));
			assembler.add(new LlvmRet(r));
		}else{
			assembler.add(new LlvmRet(typeReturnExp));
		}
		assembler.add(new LlvmCloseDefinition());
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
		return new LlvmNamedValue("identifier", new LlvmPointer(symTab.classes.get(n.name)));
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

	public LlvmValue visit(Not n){
		System.out.println("ENTER NODE - Not");
		LlvmValue v1 = n.exp.accept(this);
		LlvmValue v2 = new LlvmBool(LlvmBool.TRUE);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I1);
		assembler.add(new LlvmNot(lhs,LlvmPrimitiveType.I1,v1,v2));
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
			assembler.add(new LlvmBranch(e));
		}
		assembler.add(new LlvmLabel(e));
		
		return cond;
	}
	
	public LlvmValue visit(While n){
		System.out.println("ENTER NODE - While");
		
		LlvmValue cond = n.condition.accept(this);

		LlvmLabelValue loopWhile = new LlvmLabelValue("loopWhile" + n.line);
		LlvmLabelValue outW = new LlvmLabelValue("outW" + n.line);
		
		assembler.add(new LlvmBranch(cond, loopWhile, outW));
		assembler.add(new LlvmLabel(loopWhile));
		n.body.accept(this);
		cond = n.condition.accept(this);
		assembler.add(new LlvmBranch(cond, loopWhile, outW));
	
		assembler.add(new LlvmLabel(outW));
	
		return null;
	}

	public LlvmValue visit(Block n){
		System.out.println("ENTER NODE - Block");
		
		util.List<Statement> stm = n.body;
		for( ;stm != null; stm = stm.tail){
			stm.head.accept(this);
		}
		
		return null;
	}

	public LlvmValue visit(Assign n){
		LlvmValue opt = n.exp.accept(this);
		
		if(methodEnv.formals.containsKey(n.var.s)){
			LlvmValue val = methodEnv.formals.get(n.var.s);
			LlvmValue lhs = new LlvmRegister(val.type);
			LlvmValue formLocal = new LlvmRegister(((LlvmNamedValue)val).name+"_local",new LlvmPointer(lhs.type));
			assembler.add(new LlvmStore(opt, formLocal));
		}
		
		else if (methodEnv.vars.containsKey(n.var.s)){
			LlvmValue var = methodEnv.vars.get(n.var.s);
			assembler.add(new LlvmStore(opt, var));
		}
		
		else{
			LlvmNamedValue vthis = new LlvmNamedValue("%this", new LlvmPointer(classEnv));
			lookUp2(classEnv, opt, n, vthis);
		}
		return null;
	}

	public void lookUp2(ClassNode c, LlvmValue opt, Assign n, LlvmValue vthis){
		int i = -1;
		for(LlvmNamedValue var : c.varList){
			i++;
			
			if(var.name.equals(n.var.s)){
				LlvmRegister reg = new LlvmRegister(new LlvmPointer(var.type));
				List<LlvmValue> offsets = new ArrayList<LlvmValue>();
				LlvmValue off0 = new LlvmIntegerLiteral(0);
				LlvmValue off1 = new LlvmIntegerLiteral(i);
				offsets.add(off0);
				offsets.add(off1);
				assembler.add(new LlvmGetElementPointer(reg, vthis, offsets));
				assembler.add(new LlvmStore(opt, reg));
				return;
			}
		}
		
		LlvmNamedValue sup = classEnv.varList.get(0);
		if(sup.name.equals("%super")){
			ClassNode cnew = (ClassNode)((LlvmPointer)sup.type).content;
			LlvmRegister reg = new LlvmRegister(new LlvmPointer(cnew));
			List<LlvmValue> offsets = new ArrayList<LlvmValue>();
			LlvmValue off = new LlvmIntegerLiteral(0);
			offsets.add(off);
			offsets.add(off);
			assembler.add(new LlvmGetElementPointer(reg, vthis, offsets));
			lookUp2(cnew, opt, n, reg);
		}
	}

	public LlvmValue visit(ArrayAssign n){		
		List<LlvmValue> offsets = new ArrayList<LlvmValue>();
		LlvmValue v = n.value.accept(this);
		LlvmValue vt = n.value.type.accept(this);
		LlvmValue off = n.index.accept(this);
		
		if (methodEnv.vars.containsKey(n.var.s)){
			LlvmValue var = methodEnv.vars.get(n.var.s);
			offsets.add(off);
			LlvmValue p = new LlvmRegister(new LlvmPointer(vt.type));
			LlvmValue value = new LlvmRegister(new LlvmPointer(vt.type));
			assembler.add(new LlvmLoad(p, var));
			assembler.add(new LlvmGetElementPointer(value, p, offsets));
			assembler.add(new LlvmStore(v, value));
		}
		
		else
		{	
			LlvmNamedValue vthis = new LlvmNamedValue("%this", new LlvmPointer(classEnv));
			offsets.add(off);
			lookUp(classEnv, v, n, vthis, offsets);
		}
		return null;
	}
	
	public void lookUp(ClassNode c, LlvmValue opt, ArrayAssign n, LlvmValue vthis, List<LlvmValue> offs){
		int i = -1;
		for(LlvmNamedValue var : c.varList){
			i++;
			
			if(var.name.equals(n.var.s)){
				LlvmValue vt = n.value.type.accept(this);
				LlvmRegister reg = new LlvmRegister(new LlvmPointer(var.type));
				LlvmValue pointer = new LlvmRegister(new LlvmPointer(vt.type));
				LlvmValue value = new LlvmRegister(new LlvmPointer(vt.type));
				LinkedList<LlvmValue> offsets = new LinkedList<>();
				LlvmValue off0 = new LlvmIntegerLiteral(0);
				LlvmValue off1 = new LlvmIntegerLiteral(i);
				offsets.add(off0);
				offsets.add(off1);
				assembler.add(new LlvmGetElementPointer(reg, vthis, offsets));
				assembler.add(new LlvmLoad(pointer, reg));
				assembler.add(new LlvmGetElementPointer(value, pointer, offs));
				assembler.add(new LlvmStore(opt, value));
				return;
			}
		}
		
		LlvmNamedValue sup = classEnv.varList.get(0);
		if(sup.name.equals("%super")){
			LinkedList<LlvmValue> offsets = new LinkedList<>();
			LlvmValue off = new LlvmIntegerLiteral(0);
			offsets.add(off);
			offsets.add(off);
			ClassNode cnew = (ClassNode)((LlvmPointer)sup.type).content;
			LlvmRegister reg = new LlvmRegister(new LlvmPointer(cnew));
			assembler.add(new LlvmGetElementPointer(reg, vthis,	offsets));
			lookUp(cnew, opt, n, reg, offs);
		}
	}

	public LlvmValue visit(ArrayLookup n){
		List<LlvmValue> offsets = new ArrayList<LlvmValue>();
		LlvmValue off = n.index.accept(this);
		LlvmValue t = n.array.type.accept(this);
		LlvmValue vt = n.type.accept(this);
		LlvmValue array = n.array.accept(this);
		offsets.add(off);
		
		LlvmValue p = new LlvmRegister(t.type);
		assembler.add(new LlvmGetElementPointer(p, array, offsets));
		LlvmValue v = new LlvmRegister(vt.type);
		assembler.add(new LlvmLoad(v, p));
		
		return v;
	}

	public LlvmValue visit(ArrayLength n){
		LinkedList<LlvmValue> offsets = new LinkedList<>();
		LlvmValue array = n.array.accept(this);
		LlvmValue off = new LlvmIntegerLiteral(0);
		offsets.add(off);
		LlvmRegister v = new LlvmRegister(new LlvmPointer(LlvmPrimitiveType.I32));
		LlvmRegister lenght = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmGetElementPointer(v, array, offsets));
		assembler.add(new LlvmLoad(lenght, v));
		return lenght;
	}

	public LlvmValue visit(Call n){
		LlvmValue obj = n.object.accept(this);
		ClassNode objClass = (ClassNode)((LlvmPointer)obj.type).content;

		MethodNode method = objClass.methods.get(n.method.s);
		LlvmValue oldobj;
		while(method == null){
			objClass = (ClassNode)((LlvmPointer)objClass.varList.get(0).type).content;
			oldobj = obj;
			obj = new LlvmRegister(new LlvmPointer(objClass));
			LinkedList<LlvmValue> offsets = new LinkedList<>();
			LlvmValue off = new LlvmIntegerLiteral(0);
			offsets.add(off);
			offsets.add(off);
			assembler.add(new LlvmGetElementPointer(obj, oldobj,offsets));
			method = objClass.methods.get(n.method.s);
		}
		LlvmRegister lhs = new LlvmRegister(method.type);

		String fnName = "@__"+n.method.s+"_"+objClass.name;

		List<LlvmValue> args = new ArrayList<LlvmValue>();
		args.add(obj);

		for(util.List<syntaxtree.Exp> e = n.actuals; e != null; e = e.tail){
			LlvmValue arg = e.head.accept(this);
			if (arg.type instanceof LlvmPointer){
				if(((LlvmPointer)arg.type).content instanceof ClassNode){
					while(true){
						boolean has = false;
						String p = "";
						for(LlvmValue val : method.formals.values()){
							p += val.type.toString() + " ";
							if (val.type.toString().equals(arg.type.toString())){
								has = true;
								break;
							}
						}
						if (has){
							System.out.println("0");
							break;
						}
						else{
							System.out.println(p + " - " + arg.type.toString());
							ClassNode classNode = (ClassNode)((LlvmPointer)arg.type).content;
							classNode = symTab.classes.get(classNode.superClassName);
							LinkedList<LlvmValue> offsets = new LinkedList<>();
							LlvmValue off = new LlvmIntegerLiteral(0);
							offsets.add(off);
							offsets.add(off);
							LlvmRegister reg = new LlvmRegister(new LlvmPointer(classNode));
							assembler.add(new LlvmGetElementPointer(reg, arg, offsets));
							arg = reg;
						}
					}
				}
			}
			args.add(arg);
		}
		assembler.add(new LlvmCall(lhs, lhs.type, fnName, args));
		return lhs;
	}

	public LlvmValue visit(IdentifierExp n){
		if(methodEnv.formals.containsKey(n.name.s)){
			LlvmValue val = methodEnv.formals.get(n.name.s);
			LlvmValue lhs = new LlvmRegister(val.type);
			LlvmValue formLocal = new LlvmRegister(((LlvmNamedValue)val).name+"_local",new LlvmPointer(lhs.type));
			assembler.add(new LlvmLoad(lhs, formLocal));
			System.out.println(formLocal.type);
			return(lhs);
		}
		else if (methodEnv.vars.containsKey(n.name.s)){
			LlvmValue var = methodEnv.vars.get(n.name.s);
			LlvmValue lhs = new LlvmRegister(var.type);
			assembler.add(new LlvmLoad(lhs, var));
			lhs.type = ((LlvmPointer)lhs.type).content;
			return lhs;
		}
		else{
			LlvmNamedValue vthis = new LlvmNamedValue("%this", new LlvmPointer(classEnv));
			return recursiveLookUpId(classEnv, n, vthis);
		}
	}

	public LlvmValue recursiveLookUpId(ClassNode c, IdentifierExp n, LlvmValue vthis){
		int i = -1;
		for(LlvmNamedValue var : c.varList){
			i++;
			if(var.name.equals(n.name.s)){
				LlvmRegister ptr = new LlvmRegister(new LlvmPointer(var.type));
				LlvmRegister lhs = new LlvmRegister(var.type);
				LinkedList<LlvmValue> offsets = new LinkedList<>();
				LlvmValue off0 = new LlvmIntegerLiteral(0);
				LlvmValue off1 = new LlvmIntegerLiteral(i);
				offsets.add(off0);
				offsets.add(off1);
				assembler.add(new LlvmGetElementPointer(ptr, vthis,
						offsets));
				assembler.add(new LlvmLoad(lhs, ptr));
				return lhs;
			}
		}
		LlvmNamedValue sup = classEnv.varList.get(0);
		if(sup.name.equals("%super")){
			ClassNode cnew = (ClassNode)((LlvmPointer)sup.type).content;
			LlvmRegister reg = new LlvmRegister(new LlvmPointer(cnew));
			LinkedList<LlvmValue> offsets = new LinkedList<>();
			LlvmValue off = new LlvmIntegerLiteral(0);
			offsets.add(off);
			offsets.add(off);
			assembler.add(new LlvmGetElementPointer(reg, vthis,
					offsets));
			return recursiveLookUpId(cnew, n, reg);
		}
		return null;
	}

	public LlvmValue visit(NewArray n){
		LinkedList<LlvmValue> offsets = new LinkedList<>();
		LlvmValue s = n.size.accept(this);
		LlvmValue off = new LlvmIntegerLiteral(0);
		offsets.add(off);
		LlvmValue t = n.type.accept(this);
		LlvmValue st = n.size.type.accept(this);
		
		LlvmValue v1 = new LlvmRegister(t.type);
		assembler.add(new LlvmMalloc(v1,st.type,s));
		LlvmRegister v2 = new LlvmRegister(new LlvmPointer(LlvmPrimitiveType.I32));
		assembler.add(new LlvmGetElementPointer(v2, v1, offsets));
		assembler.add(new LlvmStore(s, v2));
		
		return v2; 
	}

	public LlvmValue visit(NewObject n){
		ClassNode classNode = symTab.classes.get(n.className.s);
		LlvmRegister obj = new LlvmRegister(new LlvmPointer(classNode));
		assembler.add(new LlvmMalloc(obj, classNode.classType, classNode.toString()));
		return obj;
	}

	public LlvmValue visit(Identifier n){
		System.out.println("ENTER NODE - Identifier");
		return null;
	}
}


/**********************************************************************************/
					/* === Tabela de Símbolos ====  */
/**********************************************************************************/

class SymTab extends VisitorAdapter{
	public Map<String, ClassNode> classes;
	private Codegen codegen;
	private ClassNode current;

	public SymTab(Codegen codegen){
		classes = new HashMap<String, ClassNode>();
		this.codegen = codegen;
	}

	public LlvmValue FillTabSymbol(Program n){
		n.accept(this);
		return null;
	}
	
	public LlvmValue visit(Program n){
		n.mainClass.accept(this);
		
		util.List<ClassDecl> c = n.classList;
		for (; c != null; c = c.tail)
			c.head.accept(this);

		return null;
	}

	public LlvmValue visit(MainClass n){
		classes.put(n.className.s, new ClassNode(n.className.s, null, null, null));
		return null;
	}

	public LlvmValue visit(ClassDeclSimple n){
		List<LlvmType> typeList = new ArrayList<LlvmType>();
		List<LlvmNamedValue> varList = new ArrayList<LlvmNamedValue>();
		
		// Percorre os atributos da classe
		util.List<VarDecl> vars = n.varList;
		for ( ; vars !=null; vars = vars.tail){	
			LlvmValue type = vars.head.type.accept(this);
			vars.head.accept(this);
			
			typeList.add(type.type);
			varList.add(new LlvmNamedValue(vars.head.name.toString(), type.type));
		}

		// Percorre os métodos da classe
		Map<String, MethodNode> metList = new HashMap<String, MethodNode>();
		util.List<MethodDecl> mtd = n.methodList;
		for (; mtd != null; mtd = mtd.tail){
			if (!metList.containsKey(mtd.head.name.s))
				metList.put(mtd.head.name.s, (MethodNode)mtd.head.accept(this));
		}
		
		if(classes.containsKey(n.name.s)){
			current = classes.get(n.name.s);
			current.classType = new LlvmStructure(typeList); 
			current.varList = varList;
			current.methods = metList;
		}
		else{
			current = new ClassNode(n.name.s, new LlvmStructure(typeList), varList, metList);
			classes.put(n.name.s, current);
		}

		return null;
	}

	public LlvmValue visit(MethodDecl n){
		LlvmType type = n.returnType.accept(codegen).type;
		Map<String, LlvmValue> listFormals = new HashMap<String, LlvmValue>();
		List<String> listFormalsType = new LinkedList<String>();
		Map<String, LlvmValue> listLocals = new HashMap<String, LlvmValue>();
		
		// this
		listFormals.put("this",new LlvmNamedValue("%this", new LlvmPointer(current)));
		listFormalsType.add(new LlvmPointer(current).toString());
		
		// params
		util.List<Formal> formals = n.formals;
		for (; formals != null; formals = formals.tail){
			LlvmValue v = formals.head.accept(this);
			LlvmType t = formals.head.type.accept(codegen).type;
			
			listFormals.put(formals.head.name.toString(), v);
			listFormalsType.add(t.toString());
		}
		
		// locals
		util.List<VarDecl> locals = n.locals;
		for (; locals != null; locals = locals.tail){
			listLocals.put(locals.head.name.toString(), locals.head.accept(this));
		}
		
		return new MethodNode(listFormals, listFormalsType, listLocals, type);
	}
	
	public LlvmValue visit(ClassDeclExtends n){
		List<LlvmType> typeList = new LinkedList<LlvmType>();
		List<LlvmNamedValue> varList = new LinkedList<LlvmNamedValue>();
	
		typeList.add(classes.get(n.superClass.s));
		varList.add(new LlvmNamedValue("%super",new LlvmPointer(classes.get(n.superClass.s))));
		
		util.List<VarDecl> vars = n.varList;
		for (; vars != null; vars = vars.tail){	
			LlvmValue type = vars.head.type.accept(this);
			vars.head.accept(this);
			
			typeList.add(type.type);
			varList.add(new LlvmNamedValue(vars.head.name.toString(), type.type));
		}
		
		// Percorre os métodos da classe
		Map<String, MethodNode> metList = new HashMap<String, MethodNode>();
		util.List<MethodDecl> mtd = n.methodList;
		for (; mtd != null; mtd = mtd.tail){
			if (!metList.containsKey(mtd.head.name.s))
				metList.put(mtd.head.name.s, (MethodNode)mtd.head.accept(this));
		}
		
		if(classes.containsKey(n.name.s)){
			current = classes.get(n.name.s);
			current.classType = new LlvmStructure(typeList); 
			current.varList = varList;
			current.methods = metList;
			current.superClassName = n.superClass.s;
		}
		else{
			current = new ClassNode(n.name.s, new LlvmStructure(typeList), varList, metList);
			current.superClassName = n.superClass.s;
			classes.put(n.name.s, current);
		}

		return null;
	}
	
	public LlvmValue visit(VarDecl n){
		return new LlvmNamedValue(n.name.toString(), n.type.accept(this).type);
	}

	public LlvmValue visit(IdentifierType n){
		return new LlvmNamedValue("", new LlvmPointer(classes.get(n.name)));
	}
	
	public LlvmValue visit(Formal n){
		return new LlvmNamedValue("%" + n.name, n.type.accept(this).type);
	}

	public LlvmValue visit(IntArrayType n){
		return new LlvmNamedValue("intArray", new LlvmPointer(LlvmPrimitiveType.I32));
	}

	public LlvmValue visit(BooleanType n){
		return new LlvmNamedValue("boolean", LlvmPrimitiveType.I1);
	}

	public LlvmValue visit(IntegerType n){
		return new LlvmNamedValue("int", LlvmPrimitiveType.I32);
	}
}

class ClassNode extends LlvmType {
	public String name;
	public String superClassName;
	public LlvmStructure classType;
	public List<LlvmNamedValue> varList;
	public Map<String, MethodNode> methods;

	ClassNode(){
	}
	
	ClassNode (String name, LlvmStructure classType, List<LlvmNamedValue> varList, Map<String, MethodNode> meth){
		this.name = name;
		this.classType = classType;
		this.varList = varList;
		this.methods = meth;
		this.superClassName = null;
	}

	public String toString(){
		return "%class." + name;
	}
}

class MethodNode extends LlvmValue{
	public Map<String, LlvmValue> formals;
	public List<String> formalTypes;
	public Map<String, LlvmValue> vars;

	public MethodNode(){
		formals = new HashMap<String, LlvmValue>();
		formalTypes = new ArrayList<String>();
		vars = new HashMap<String, LlvmValue>();
	}
	
	public MethodNode(Map<String, LlvmValue> formals, List<String> formalTypes, Map<String, LlvmValue> vars, LlvmType returnType){
		this.formals = formals;
		this.formalTypes = formalTypes;
		this.vars = vars;
		this.type = returnType;
	}
}




