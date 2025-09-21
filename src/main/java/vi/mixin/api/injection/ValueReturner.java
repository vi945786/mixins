package vi.mixin.api.injection;

public class ValueReturner<R> extends Returner {

    private R returnValue = null;

    public void setReturnValue(R returnValue) {
        this.returnValue = returnValue;
        returned = true;
    }

    public void doReturn() {
        throw new UnsupportedOperationException("Use ValueReturner.setReturnValue");
    }

    public R getReturnValue() {
        return this.returnValue;
    }

    public ValueReturner(         ) {                                  }
    public ValueReturner(R       r) { returnValue =                 r; }
    public ValueReturner(byte    r) { returnValue = (R) (Byte)      r; }
    public ValueReturner(char    r) { returnValue = (R) (Character) r; }
    public ValueReturner(double  r) { returnValue = (R) (Double)    r; }
    public ValueReturner(float   r) { returnValue = (R) (Float)     r; }
    public ValueReturner(int     r) { returnValue = (R) (Integer)   r; }
    public ValueReturner(long    r) { returnValue = (R) (Long)      r; }
    public ValueReturner(short   r) { returnValue = (R) (Short)     r; }
    public ValueReturner(boolean r) { returnValue = (R) (Boolean)   r; }

    public byte    getReturnValueB() { if (this.returnValue == null) { return 0;     } return (Byte)      this.returnValue; }
    public char    getReturnValueC() { if (this.returnValue == null) { return 0;     } return (Character) this.returnValue; }
    public double  getReturnValueD() { if (this.returnValue == null) { return 0.0;   } return (Double)    this.returnValue; }
    public float   getReturnValueF() { if (this.returnValue == null) { return 0.0F;  } return (Float)     this.returnValue; }
    public int     getReturnValueI() { if (this.returnValue == null) { return 0;     } return (Integer)   this.returnValue; }
    public long    getReturnValueJ() { if (this.returnValue == null) { return 0;     } return (Long)      this.returnValue; }
    public short   getReturnValueS() { if (this.returnValue == null) { return 0;     } return (Short)     this.returnValue; }
    public boolean getReturnValueZ() { if (this.returnValue == null) { return false; } return (Boolean)   this.returnValue; }
}
