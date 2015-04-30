package llvmast;
public  class LlvmBranch extends LlvmInstruction{
	
	public LlvmLabelValue brTrue;
	public LlvmLabelValue brFalse;
	public LlvmValue cond;
	
    public LlvmBranch(LlvmLabelValue label){
    	this.brTrue = label;
    }
    
    public LlvmBranch(LlvmValue cond,  LlvmLabelValue brTrue, LlvmLabelValue brFalse){
    	this.cond = cond;
    	this.brTrue = brTrue;
    	this.brFalse = brFalse;
    }

    public String toString(){
		if(cond != null)
			return "  " + "br i1 " + cond + ", label %" + brTrue + ", label %" + brFalse; 
		else
			return "  " + "br label %" + brTrue;
    }
}