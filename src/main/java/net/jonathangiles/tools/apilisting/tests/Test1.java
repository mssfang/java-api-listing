package net.jonathangiles.tools.apilisting.tests;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class Test1 {
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


    public Test1() {

    }

    public Test1(String text) {

    }

    public List<String> getStringList() {
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
}
