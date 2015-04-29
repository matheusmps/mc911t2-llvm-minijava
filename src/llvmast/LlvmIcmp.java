package llvmast;
public  class LlvmIcmp extends LlvmInstruction{
	 	public LlvmRegister lhs;
	 	private int conditionCode;
	    public LlvmType type;
	    public LlvmValue op1, op2;
    public LlvmIcmp(LlvmRegister lhs, int conditionCode, LlvmType type, LlvmValue op1, LlvmValue op2){
    	this.lhs = lhs;
    	this.conditionCode = conditionCode;
    	this.type = type;
    	this.op1 = op1;
    	this.op2 = op2;
    }

    public String toString(){
    	if(conditionCode == 1)
    		return "  " +lhs + " = icmp eq " + type + " " + op1 + ", " + op2;
    	if(conditionCode == 2)
    		return "  " +lhs + " = icmp slt " + type + " " + op1 + ", " + op2;
    	return "error";
    }
}