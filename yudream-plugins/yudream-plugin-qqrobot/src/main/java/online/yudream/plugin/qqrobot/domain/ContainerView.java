package online.yudream.plugin.qqrobot.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 背包之外的容器快照：潜影盒、末影箱。
 *
 * <p>同一个玩家可能背多个潜影盒，且末影箱不在物品里，所以不能只靠
 * {@link ItemView#container()} 推断。服务端显式上报容器列表，后台按顺序
 * 依次渲染，标题带上来源槽位，避免“盒子太多分不清哪个是哪个”。</p>
 *
 * @param kind       {@code shulker} / {@code ender_chest} / 其他（按通用容器处理）
 * @param label      展示名，服务端给物品自定义名或“末影箱”
 * @param sourceSlot 来源槽位描述，例如 {@code 快捷栏 3}、{@code 背包 12}
 * @param items      27 格内容，不足补空气
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainerView(String kind, String label, String sourceSlot, List<ItemView> items) {
    public ContainerView normalized() {
        return new ContainerView(
                kind == null || kind.isBlank() ? "container" : kind.trim(),
                label == null ? "" : label.trim(),
                sourceSlot == null ? "" : sourceSlot.trim(),
                items == null ? List.of() : items.stream().map(ItemView::normalized).toList()
        );
    }

    public boolean empty() {
        return items.stream().allMatch(ItemView::empty);
    }

    public int filled() {
        return (int) items.stream().filter(item -> !item.empty()).count();
    }
}
