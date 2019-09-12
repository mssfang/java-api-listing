package net.jonathangiles.tools.apilisting.tests;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class Test1<T extends Comparable<D>> {
    public String name;
    public int val;
    public static final String STATIC_FINAL = "sss";
    public final int intFinal = 1;
    public static char charStatidc = 'a';
    protected String helleWord;
    public Set<String> set;
    public String a,b,c;

    private int privateInt;
    String packagePrivate;

    public Test1() throws Exception, RuntimeException{

    }

    public Test1(String text) {

    }

    public Test1(int[] text) {

    }

    public Test1(String[] text, String[][] strings, Set<String>[] sets) {

    }


    public int[] retutnIntArray() {
        return null;
    }

    public String[] returnStringArray() {
        return null;
    }

    public List<List<String>> getStringList() throws Exception, RuntimeException {
        return null;
    }

    public Map<String,Integer> getStringIntegerMap() {
        return null;
    }

    public void setStringList(List<String> list) {

    }

    public void setStringIntegerMap(Map<String, Integer> map) {

    }

    public void setArgs(String name, int age) {

    }

    public <T> T getT(Supplier<T> supplier) {
        return supplier.get();
    }

    public A returnA () {
        return null;
    }


    public static <T extends Comparable<T>> T maximum(T x, T y, T z) {
        T max = x;   // assume x is initially the largest

        if(y.compareTo(max) > 0) {
            max = y;   // y is the largest so far
        }

        if(z.compareTo(max) > 0) {
            max = z;   // z is the largest now
        }
        return max;   // returns the largest object
    }

    public static abstract class InnerClass<A, B> extends Exception implements A, C {
        public String name;
        public int val;
        public static final String STATIC_FINAL = "sss";
        public final int intFinal = 1;
        protected String helleWord;
        public Set<String> set;
        public String a,b,c;

        private int privateInt;
        String packagePrivate;

        public InnerClass() { }

        public InnerClass(String text) { }

        public List<String> getStringListInnerClass() {
            return null;
        }

        public Map<String, Integer> getStringIntegerMapInnerClass() {
            return null;
        }

        public Map<Set<Map<Double, String>>, Map<Long, Integer>> getStringIntegerMapInnerClass() {
            return null;
        }


        public void setStringListInnerClass(List<String> list) { }

        public void setStringIntegerMapInnerClass(Map<String, Integer> map) { }

        public void setArgsInnerClass(String name, int age) { }

        public <T> T getTInnerClass(Supplier<T> supplier) {
            return supplier.get();
        }

        public interface B {

        }
    }

    public interface A<F, G> {

    }

    public interface C {

    }

    public enum UserStatus { // Basic Enum
        PENDING,
        ACTIVE,
        INACTIVE,
        DELETED;
    }

    public enum WhoisRIR { // Enum + Instance field
        ARIN("whois.arin.net"),
        RIPE("whois.ripe.net"),
        APNIC("whois.apnic.net"),
        AFRINIC("whois.afrinic.net"),
        LACNIC("whois.lacnic.net"),
        JPNIC("whois.nic.ad.jp"),
        KRNIC("whois.nic.or.kr"),
        CNNIC("ipwhois.cnnic.cn"),
        UNKNOWN("");

        private String url;

        WhoisRIR(String url) {
            this.url = url;
        }

        public String url() {
            return url;
        }
    }

    public enum Operation { // Enum + Method + Some logic
        PLUS,
        MINUS,
        TIMES,
        DIVIDE;

        double calculate(double x, double y) {
            switch (this) {
                case PLUS:
                    return x + y;
                case MINUS:
                    return x - y;
                case TIMES:
                    return x * y;
                case DIVIDE:
                    return x / y;
                default:
                    throw new AssertionError("Unknown operations " + this);
            }
        }

    }

}
public enum UserStatus { // Basic Enum
    PENDING,
    ACTIVE,
    INACTIVE,
    DELETED;
}

public enum WhoisRIR { // Enum + Instance field
    ARIN("whois.arin.net"),
    RIPE("whois.ripe.net"),
    APNIC("whois.apnic.net"),
    AFRINIC("whois.afrinic.net"),
    LACNIC("whois.lacnic.net"),
    JPNIC("whois.nic.ad.jp"),
    KRNIC("whois.nic.or.kr"),
    CNNIC("ipwhois.cnnic.cn"),
    UNKNOWN("");

    private String url;

    WhoisRIR(String url) {
        this.url = url;
    }

    public String url() {
        return url;
    }
}

public enum Operation { // Enum + Method + Some logic
    PLUS,
    MINUS,
    TIMES,
    DIVIDE;

    double calculate(double x, double y) {
        switch (this) {
            case PLUS:
                return x + y;
            case MINUS:
                return x - y;
            case TIMES:
                return x * y;
            case DIVIDE:
                return x / y;
            default:
                throw new AssertionError("Unknown operations " + this);
        }
    }

}