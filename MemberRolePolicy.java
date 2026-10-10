import java.util.Map;

/** Member policy implementation; demonstrates inheritance and overridden behavior. */
public final class MemberRolePolicy extends BaseRolePolicy {
  @Override public boolean canManageUsers(Map<String,Object> user){return false;}
  @Override public String roleName(){return "Member";}
}
