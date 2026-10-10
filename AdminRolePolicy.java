import java.util.Map;

/** Administrator policy implementation. */
public final class AdminRolePolicy extends BaseRolePolicy {
  @Override public boolean canManageUsers(Map<String,Object> user){return "admin".equals(user.getOrDefault("role","member"));}
  @Override public String roleName(){return "Administrator";}
}
