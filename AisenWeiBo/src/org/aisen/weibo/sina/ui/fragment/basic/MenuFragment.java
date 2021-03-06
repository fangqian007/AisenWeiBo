package org.aisen.weibo.sina.ui.fragment.basic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;

import com.m.common.utils.ActivityHelper;
import com.m.common.utils.Logger;
import com.m.component.bitmaploader.BitmapLoader;
import com.m.component.bitmaploader.core.ImageConfig;
import com.m.network.task.TaskException;
import com.m.network.task.WorkTask;
import com.m.support.adapter.ABaseAdapter;
import com.m.support.inject.ViewInject;
import com.m.ui.fragment.AListFragment;

import org.aisen.weibo.sina.R;
import org.aisen.weibo.sina.base.AppContext;
import org.aisen.weibo.sina.base.AppSettings;
import org.aisen.weibo.sina.support.bean.AccountBean;
import org.aisen.weibo.sina.support.bean.MenuBean;
import org.aisen.weibo.sina.support.db.AccountDB;
import org.aisen.weibo.sina.support.db.PublishDB;
import org.aisen.weibo.sina.support.publisher.PublishManager;
import org.aisen.weibo.sina.support.utils.AisenUtils;
import org.aisen.weibo.sina.support.utils.ImageConfigUtils;
import org.aisen.weibo.sina.sys.service.UnreadService;
import org.aisen.weibo.sina.ui.activity.basic.MainActivity;
import org.aisen.weibo.sina.ui.fragment.account.AccountFragment;
import org.sina.android.bean.WeiBoUser;

import java.util.ArrayList;
import java.util.List;

/**
 * 左侧抽屉菜单
 *
 * Created by wangdan on 15/4/14.
 */
public class MenuFragment extends AListFragment<MenuBean, ArrayList<MenuBean>> {

    public static MenuFragment newInstance(String type) {
        MenuFragment fragment = new MenuFragment();

        if (!TextUtils.isEmpty(type)) {
            Bundle args = new Bundle();
            args.putString("type", type);
            fragment.setArguments(args);
        }

        return fragment;
    }

    private MenuBean lastSelectedMenu;

    private int draftSize;

    private View profileHeader;

    @Override
    protected int inflateContentView() {
        return R.layout.as_ui_menu;
    }

    @Override
    protected ABaseAdapter.AbstractItemView<MenuBean> newItemView() {
        return new MenuItemView();
    }

    @Override
    protected void requestData(RefreshMode mode) {

    }

    @Override
    protected void config(RefreshConfig config) {
        super.config(config);

        profileHeader = View.inflate(getActivity(), R.layout.as_lay_leftmenu, null);

        View btnAccounts = profileHeader.findViewById(R.id.btnAccount);
        btnAccounts.setOnClickListener(accountSwitchOnClickListener);

        setAccountItem();

        getListView().addHeaderView(profileHeader);
    }

    @Override
    protected void layoutInit(LayoutInflater inflater, Bundle savedInstanceSate) {
        super.layoutInit(inflater, savedInstanceSate);

        getListView().setOnItemClickListener(this);

        setItems(MenuGenerator.generateMenus());

        if (savedInstanceSate == null) {
            int index = getListView().getHeaderViewsCount();

            lastSelectedMenu = MenuGenerator.generateMenu("1");
            if (getArguments() != null) {
                String type = getArguments().getString("type");

                lastSelectedMenu = MenuGenerator.generateMenu(type);
                for (int i = 0; i < getAdapterItems().size(); i++) {
                    MenuBean bean = getAdapterItems().get(i);
                    if (bean.getType().equals(type)) {
                        lastSelectedMenu = bean;
                        index = i + getListView().getHeaderViewsCount();
                        break;
                    }
                }

            }

            if (index <= getListView().getHeaderViewsCount()) {
                onMenuClicked(lastSelectedMenu, null);
            }
            else {
                onItemClick(getListView(), null, index, index);
            }
        }
        else {
            lastSelectedMenu = (MenuBean) savedInstanceSate.getSerializable("lastSelectedMenu");
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable("lastSelectedMenu", lastSelectedMenu);
    }

    public void setAccountItem() {
        WeiBoUser user = AppContext.getUser();

        // 头像
        ImageView imgPhoto = (ImageView) profileHeader.findViewById(R.id.imgPhoto);
        imgPhoto.setOnClickListener(viewOnClickListener);
        BitmapLoader.getInstance().display(MenuFragment.this,
                user.getAvatar_large(), imgPhoto, ImageConfigUtils.getLargePhotoConfig());
        // 名字
        TextView txtName = (TextView) profileHeader.findViewById(R.id.txtName);
        txtName.setText(user.getScreen_name());

        // 背景
        ImageConfig coverConfig = new ImageConfig();
        coverConfig.setLoadfaildRes(R.drawable.bg_banner_dialog);
        coverConfig.setLoadingRes(R.drawable.bg_banner_dialog);
        final ImageView imgCover = (ImageView) profileHeader.findViewById(R.id.imgCover);
        BitmapLoader.getInstance().display(this, user.getCover_image_phone(), imgCover, coverConfig);
    }

    public void setSelectedMenu(MenuBean menu) {
        lastSelectedMenu = menu;
        notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();

        getAdapter().notifyDataSetChanged();

        if (AppContext.isLogedin())
            new RefreshDraftTask().execute();

        IntentFilter filter = new IntentFilter();
        filter.addAction(UnreadService.ACTION_UNREAD_CHANGED);
        filter.addAction(PublishManager.ACTION_PUBLISH_CHANNGED);
        getActivity().registerReceiver(receiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();

        getActivity().unregisterReceiver(receiver);
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && !TextUtils.isEmpty(intent.getAction())) {
                if (UnreadService.ACTION_UNREAD_CHANGED.equals(intent.getAction())) {
                    getAdapter().notifyDataSetChanged();
                }
                else if (PublishManager.ACTION_PUBLISH_CHANNGED.equals(intent.getAction())) {
                    new RefreshDraftTask().execute();
                }
            }
        }

    };

    private void updateCounter(TextView view, MenuBean menu) {

        view.setVisibility(View.INVISIBLE);

        int count = 0;
        switch (Integer.parseInt(menu.getType())) {
            // 提及
            case 2:
                if (AppContext.getUnreadCount() != null) {
                    if (AppSettings.isNotifyStatusMention())
                        count += AppContext.getUnreadCount().getMention_status();
                    if (AppSettings.isNotifyCommentMention())
                        count += AppContext.getUnreadCount().getMention_cmt();
                }
                break;
            // 评论
            case 3:
                if (AppContext.getUnreadCount() != null)
                    count = AppContext.getUnreadCount().getCmt();
                break;
            // 私信
            case 10:
                if (AppContext.getUnreadCount() != null)
                    count = AppContext.getUnreadCount().getDm();
                break;
            // 草稿
            case 6:
                count = draftSize;
                break;
            // 设置
            case 5:
                count = ActivityHelper.getBooleanShareData("newVersion", false) ? 1 : 0;
                break;
            default:
                break;
        }

        if (count > 0) {
            if (count > 100)
                view.setText("100+");
            else
                view.setText(String.valueOf(count));

            view.setVisibility(View.VISIBLE);
        }
    }

    class RefreshDraftTask extends WorkTask<Void, Void, Boolean> {

        @Override
        public Boolean workInBackground(Void... params) throws TaskException {
            draftSize = PublishDB.getPublishList(AppContext.getUser()).size();
            return true;
        }

        @Override
        protected void onSuccess(Boolean result) {
            super.onSuccess(result);

            getAdapter().notifyDataSetChanged();
        }

    }

    private View.OnClickListener viewOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.imgPhoto) {
//                UserProfileFragment.launch(getActivity(), AppContext.getUser());
            }
        }
    };

    private boolean onMenuClicked(MenuBean menu, View view) {
        if ("1000".equals(menu.getType()))
            return true;

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null && activity.onMenuSelected(menu, false, view)) {
            return true;
        }

        lastSelectedMenu = menu;
        getAdapter().notifyDataSetChanged();

        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
        MenuBean entity = null;

        if (position == 0) {
            entity = MenuGenerator.generateMenu("0");
            return;
        }
        else {
            entity = getAdapterItems().get(position - getListView().getHeaderViewsCount());
        }

        if ("1000".equals(entity.getType()))
            return;

        if ("0".equals(entity.getType()))
            Logger.d("查看用户信息");
        else
            Logger.d(getString(entity.getTitleRes()));

        if (onMenuClicked(entity, view))
            return;

        ((MainActivity) getActivity()).closeDrawer();
    }

    class MenuItemView extends ABaseAdapter.AbstractItemView<MenuBean> {

        @ViewInject(id = R.id.txtTitle)
        TextView txtTitle;
        @ViewInject(id = R.id.txtCounter)
        TextView txtCounter;
        @ViewInject(id = R.id.layIcon)
        View layIcon;
        @ViewInject(id = R.id.imgIcon)
        ImageView imgIcon;
        @ViewInject(id = R.id.viewDivider)
        View viewDivider;
        @ViewInject(id = R.id.layItem)
        View layItem;

        @Override
        public int inflateViewId() {
            return R.layout.as_item_menu;
        }

        @Override
        public void bindingData(View convertView, MenuBean data) {
            // 2014-09-01 解决注销账号时崩溃的BUG
            if (!AppContext.isLogedin())
                return;

            txtTitle.setText(data.getMenuTitleRes());
        }

        @Override
        public void updateConvertView(MenuBean data, View convertView, int selectedPosition) {
            super.updateConvertView(data, convertView, selectedPosition);
            if ("1000".equals(data.getType())) {
                viewDivider.setVisibility(View.VISIBLE);

                layItem.setVisibility(View.GONE);
            }
            else {
                viewDivider.setVisibility(View.GONE);

                layItem.setVisibility(View.VISIBLE);

                updateCounter(txtCounter, data);

                if (lastSelectedMenu != null && lastSelectedMenu.getType().equals(data.getType())) {
                    layItem.setBackgroundResource(R.drawable.abc_list_pressed_holo_light);
                    txtTitle.setTextColor(AisenUtils.getThemeColor(getActivity()));
                }
                else {
                    layItem.setBackgroundColor(Color.TRANSPARENT);
                    txtTitle.setTextColor(Color.parseColor("#ff676767"));
                }

                if (data.getIconRes() > 0) {
                    layIcon.setVisibility(View.VISIBLE);

                    imgIcon.setImageResource(data.getIconRes());
                }
                else {
                    layIcon.setVisibility(View.GONE);
                }
            }
        }

    }

    View.OnClickListener accountSwitchOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            final List<AccountBean> accountList = AccountDB.query();
            for (AccountBean bean : accountList) {
                if (bean.getUser().getIdstr().equals(AppContext.getUser().getIdstr())) {
                    accountList.remove(bean);
                    break;
                }
            }

            final String[] items = new String[accountList.size() + 1];
            for (int i = 0; i < accountList.size(); i++)
                items[i] = accountList.get(i).getUser().getScreen_name();
            items[items.length - 1] = getString(R.string.draw_accounts);

            AisenUtils.showMenuDialog(MenuFragment.this, v, items, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == items.length - 1) {
                        // 账号管理
                        AccountFragment.launch(getActivity());
                    } else {
                        AccountFragment.login(accountList.get(which), true);
                    }
                }
            });

        }
    };

}
