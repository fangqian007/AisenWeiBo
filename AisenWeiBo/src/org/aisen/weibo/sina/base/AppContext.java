package org.aisen.weibo.sina.base;

import org.aisen.weibo.sina.support.bean.AccountBean;
import org.aisen.weibo.sina.support.db.AccountDB;
import org.aisen.weibo.sina.sys.service.UnreadService;
import org.aisen.weibo.sina.ui.fragment.basic.BizFragment;
import org.sina.android.bean.Groups;
import org.sina.android.bean.Token;
import org.sina.android.bean.UnreadCount;
import org.sina.android.bean.WeiBoUser;

/**
 * Created by wangdan on 15/4/12.
 */
public class AppContext {

    private static AccountBean accountBean;// 当前登录的用户信息

    private static UnreadCount unreadCount;

    public static boolean isLogedin() {
        return accountBean != null;
    }

    /**
     * 刷新登录用户的用户，分组信息
     *
     * @param user
     * @param groups
     */
    public static void refresh(WeiBoUser user, Groups groups) {
        AppContext.accountBean.setUser(user);
        AppContext.accountBean.setGroups(groups);

        // 刷新DB的信息
        AccountDB.newAccount(AppContext.accountBean);
    }

    public static void login(AccountBean accountBean) {
        boolean startUnreadService = AppContext.accountBean == null ||
                !AppContext.accountBean.getUser().getIdstr().equals(accountBean.getUser().getIdstr());

        AppContext.accountBean = accountBean;

        // 未读消息重置
        if (AppContext.getUnreadCount() == null || startUnreadService) {
            AppContext.unreadCount = UnreadService.getUnreadCount();
        }
        if (AppContext.unreadCount == null)
            AppContext.unreadCount = new UnreadCount();

        // 开启未读服务
        if (startUnreadService)
            UnreadService.startService();

        // 检查更新变化
//        CheckChangedUtils.check(AppContext.getUser(), AppContext.getToken());

        // 刷新定时任务
        MyApplication.refreshPublishAlarm();

        // 处理点赞数据
        BizFragment.refreshLikeCache();
    }

    public static void logout() {
        // 停止未读服务
        UnreadService.stopService();
        // 移除定时任务
        MyApplication.removeAllPublishAlarm();
        // 退出账号
        accountBean = null;
    }

    public static Token getToken() {
        if (!isLogedin())
            return null;

        return accountBean.getToken();
    }

    public static WeiBoUser getUser() {
        if (!isLogedin())
            return null;

        return accountBean.getUser();
    }

    public static Groups getGroups() {
        if (!isLogedin())
            return null;

        return accountBean.getGroups();
    }

    public static AccountBean getAccount() {
        return AppContext.accountBean;
    }

    public static void setUnreadCount(UnreadCount unreadCount) {
        AppContext.unreadCount = unreadCount;
    }

    public static UnreadCount getUnreadCount() {
        return AppContext.unreadCount;
    }

}
