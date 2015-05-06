package llvmast;
import  java.util.*;
public class LlvmGetElementPointer extends LlvmInstruction{
    public LlvmValue lhs;
    public LlvmValue source;
    public List<LlvmValue> offsets;

    public LlvmGetElementPointer(LlvmValue lhs, LlvmValue source, List<LlvmValue> offsets){
		this.lhs = lhs;
		this.source = source;
		this.offsets = offsets;
    }
    
    public LlvmGetElementPointer(LlvmValue lhs, LlvmValue source, LlvmValue offset1){
    	this.lhs = lhs;
    	this.source = source;
    	this.offsets = new ArrayList<LlvmValue>();
    	this.offsets.add(offset1);
    }
    
    public LlvmGetElementPointer(LlvmValue lhs, LlvmValue source, LlvmValue offset1, LlvmValue offset2){
		this.lhs = lhs;
		this.source = source;
		this.offsets = new ArrayList<LlvmValue>();
		this.offsets.add(offset1);
		this.offsets.add(offset2);
    }
    
    public String toString(){
	String ps = "";
	for(int i = 0; i<offsets.size(); i++){
	    ps = ps + offsets.get(i).type + " " + offsets.get(i);
	    if(i+1<offsets.size()) 
		ps = ps + ", ";

	}
	return "  " + lhs + " = getelementptr " + source.type + " " + source +", " + ps;
    }

}
