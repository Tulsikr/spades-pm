import java.util.Map;

/** Interface for role-based authorization. Implementations demonstrate polymorphism. */
public interface RolePolicy {
  boolean canManageUsers(Map<String,Object> user);
  String roleName();
}
