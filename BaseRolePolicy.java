import java.util.Map;

/** Shared base class for workspace role policies; concrete roles inherit common helpers. */
public abstract class BaseRolePolicy implements RolePolicy {
  protected String normalizedRole(Map<String,Object> user) {
    return String.valueOf(user.getOrDefault("role", "member")).toLowerCase(java.util.Locale.ROOT);
  }
}
