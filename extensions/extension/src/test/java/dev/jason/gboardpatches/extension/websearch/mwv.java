import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class mwv {
    public final Map<String, mtr> testDescriptors = new LinkedHashMap<>();
    public final List<String> testSavedOrder = new ArrayList<>();
    public int testRegistrations;
    public boolean testLastBoolean;
    public boolean testFailRegistration;

    public final void g(mtr descriptor, boolean value) {
        if (testFailRegistration) throw new IllegalStateException("registration failure");
        testRegistrations++;
        testLastBoolean = value;
        testDescriptors.put(descriptor.b, descriptor);
    }
}
