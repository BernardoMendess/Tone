import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {

        String[] strings = {"eat", "tea", "tan", "ate", "nat", "bat"};

        List<List<String>> stringsList = new ArrayList<>();
        for(int i = 0; i < strings.length; i++){
            stringsList.add(Arrays.stream(strings[i].split("")).sorted().toList());
        }
        List<List<String>> resultadoMatriz = new ArrayList<>();
        boolean deveComparar = true;
        for(int i = 0; i < stringsList.size(); i++){
            for(List<String> lista : resultadoMatriz) {
                if (lista.contains(strings[i])){
                    deveComparar = false;
                    break;
                }
            }
            if(deveComparar) {
                List<String> resultadoLinha = new ArrayList<>();
                resultadoLinha.add(strings[i]);
                for(int j = i+1; j < stringsList.size(); j++){
                    if(Objects.equals(stringsList.get(i), stringsList.get(j))){
                        resultadoLinha.add(strings[j]);
                    }
                }
                resultadoMatriz.add(resultadoLinha);
            }
            deveComparar = true;
        }

        System.out.println("[");
        for(List<String> lista : resultadoMatriz) {
            System.out.print("[");
            for(String string : lista) {
                System.out.print(string + ",");
            }
            System.out.println("]");
        }
        System.out.println("]");
    }
}