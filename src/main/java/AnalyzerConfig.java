import java.util.HashSet;
import java.util.Set;

public class AnalyzerConfig {
    // Include lists
    private Set<String> includeExtensions = new HashSet<>();
    private Set<String> includeNamesOrPaths = new HashSet<>();

    // Exclude lists
    private Set<String> excludeExtensions = new HashSet<>();
    private Set<String> excludeNamesOrPaths = new HashSet<>();

    // Backward-compat: legacy key "excludeNames"
    private Set<String> excludeNames = new HashSet<>();

    public AnalyzerConfig() {}

    public Set<String> getIncludeExtensions() { return includeExtensions; }
    public void setIncludeExtensions(Set<String> includeExtensions) { this.includeExtensions = includeExtensions != null ? includeExtensions : new HashSet<>(); }

    public Set<String> getIncludeNamesOrPaths() { return includeNamesOrPaths; }
    public void setIncludeNamesOrPaths(Set<String> includeNamesOrPaths) { this.includeNamesOrPaths = includeNamesOrPaths != null ? includeNamesOrPaths : new HashSet<>(); }

    public Set<String> getExcludeExtensions() { return excludeExtensions; }
    public void setExcludeExtensions(Set<String> excludeExtensions) { this.excludeExtensions = excludeExtensions != null ? excludeExtensions : new HashSet<>(); }

    public Set<String> getExcludeNamesOrPaths() { return excludeNamesOrPaths; }
    public void setExcludeNamesOrPaths(Set<String> excludeNamesOrPaths) { this.excludeNamesOrPaths = excludeNamesOrPaths != null ? excludeNamesOrPaths : new HashSet<>(); }

    public Set<String> getExcludeNames() { return excludeNames; }
    public void setExcludeNames(Set<String> excludeNames) { this.excludeNames = excludeNames != null ? excludeNames : new HashSet<>(); }
}

