package com.arcticsus;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;

public final class ArcticSusPlugin extends JavaPlugin implements CommandExecutor {
    private Connection db;
    private static final String SUS="arcticsus.sus", INSPECT="arcticsus.inspect",
            TP="arcticsus.teleport", BAN="arcticsus.ban", CLEAR="arcticsus.clear";

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            db=DriverManager.getConnection("jdbc:sqlite:"+getDataFolder()+"/arcticsus.db");
            try(Statement s=db.createStatement()){
                s.executeUpdate("CREATE TABLE IF NOT EXISTS flags(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,flags INTEGER NOT NULL DEFAULT 0,last_flag INTEGER NOT NULL DEFAULT 0)");
            }
        } catch(SQLException e){ getLogger().severe("Database error: "+e.getMessage()); }
        Objects.requireNonNull(getCommand("sus")).setExecutor(this);
        getLogger().info("ArcticSus 1.0.1 enabled.");
    }

    @Override public void onDisable(){try{if(db!=null)db.close();}catch(SQLException ignored){}}

    public void flag(UUID uuid,String name){
        if(db==null)return;
        try(PreparedStatement p=db.prepareStatement(
                "INSERT INTO flags(uuid,name,flags,last_flag) VALUES(?,?,1,?) "+
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,flags=flags+1,last_flag=excluded.last_flag")){
            p.setString(1,uuid.toString()); p.setString(2,name==null?"Unknown":name);
            p.setLong(3,System.currentTimeMillis()); p.executeUpdate();
        }catch(SQLException e){getLogger().warning("Flag failed: "+e.getMessage());}
    }

    @Override public boolean onCommand(@NotNull CommandSender s,@NotNull Command c,@NotNull String l,@NotNull String[] a){
        if(!(s instanceof Player p)){s.sendMessage("ArcticSus is player-only.");return true;}
        if(a.length==0){if(!p.hasPermission(SUS)){deny(p);return true;} main(p);return true;}
        if(a.length<2)return true;
        UUID u;try{u=UUID.fromString(a[1]);}catch(Exception e){return true;}
        switch(a[0].toLowerCase(Locale.ROOT)){
            case "inspect"->{if(p.hasPermission(INSPECT))inspect(p,u);else deny(p);}
            case "teleport"->{if(p.hasPermission(TP)){Player t=Bukkit.getPlayer(u);if(t!=null)p.teleportAsync(t.getLocation());else p.sendRichMessage("<red>Player is offline.</red>");}else deny(p);}
            case "banconfirm"->{if(p.hasPermission(BAN)){R r=get(u);if(r!=null)banConfirm(p,r);}else deny(p);}
            case "ban"->{if(p.hasPermission(BAN))ban(p,u);else deny(p);}
            case "clear"->{if(p.hasPermission(CLEAR))clear(p,u);else deny(p);}
            default->{if(p.hasPermission(SUS))main(p);}
        }
        return true;
    }

    private void deny(Player p){p.sendRichMessage(getConfig().getString("messages.no-permission","<red>No permission.</red>"));}

    private void main(Player p){
        List<R> rs=list(); List<DialogBody> body=new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Repeated detections • click a player to investigate",NamedTextColor.GRAY)));
        if(rs.isEmpty())body.add(DialogBody.plainMessage(Component.text("No suspicious players are currently flagged.",NamedTextColor.GREEN)));
        for(R r:rs){
            Component clickable=Component.text("  "+r.name+"  •  "+r.flags+" flags",color(r.flags))
                    .decorate(TextDecoration.BOLD)
                    .append(Component.text("\n  Click to investigate",NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.runCommand("/sus inspect "+r.uuid));
            body.add(DialogBody.item(head(r.uuid),DialogBody.plainMessage(clickable),false,true,360,64));
        }
        List<ActionButton> acts=new ArrayList<>();
        acts.add(ActionButton.builder(Component.text("Refresh",NamedTextColor.AQUA)).width(120)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/sus"))).build());
        acts.add(ActionButton.builder(Component.text("Close",NamedTextColor.GRAY)).width(120).build());
        Dialog d=Dialog.create(b->b.empty().base(DialogBase.builder(Component.text("❄ ARCTIC SUS",NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                .body(body).canCloseWithEscape(true).build()).type(DialogType.multiAction(acts,null,2)));
        p.showDialog(d);
    }

    private void inspect(Player p,UUID u){
        R r=get(u);if(r==null){main(p);return;}
        List<DialogBody> body=List.of(
                DialogBody.item(head(u),DialogBody.plainMessage(Component.text(r.name,NamedTextColor.WHITE).decorate(TextDecoration.BOLD)),false,true,360,72),
                DialogBody.plainMessage(Component.text("Suspicion: ",NamedTextColor.GRAY).append(Component.text(level(r.flags),color(r.flags)).decorate(TextDecoration.BOLD))),
                DialogBody.plainMessage(Component.text("Flags: ",NamedTextColor.GRAY).append(Component.text(String.valueOf(r.flags),NamedTextColor.WHITE))),
                DialogBody.plainMessage(Component.text("Last detection: ",NamedTextColor.GRAY).append(Component.text(age(r.last),NamedTextColor.WHITE)))
        );
        List<ActionButton> a=new ArrayList<>();
        if(p.hasPermission(TP))a.add(ActionButton.builder(Component.text("TPA / Teleport",NamedTextColor.AQUA)).width(150).action(DialogAction.staticAction(ClickEvent.runCommand("/sus teleport "+u))).build());
        if(p.hasPermission(BAN))a.add(ActionButton.builder(Component.text("Ban",NamedTextColor.RED)).width(110).action(DialogAction.staticAction(ClickEvent.runCommand("/sus banconfirm "+u))).build());
        if(p.hasPermission(CLEAR))a.add(ActionButton.builder(Component.text("Clear Stats",NamedTextColor.YELLOW)).width(140).action(DialogAction.staticAction(ClickEvent.runCommand("/sus clear "+u))).build());
        a.add(ActionButton.builder(Component.text("Back",NamedTextColor.GRAY)).width(100).action(DialogAction.staticAction(ClickEvent.runCommand("/sus"))).build());
        p.showDialog(Dialog.create(b->b.empty().base(DialogBase.builder(Component.text("❄ PLAYER INVESTIGATION",NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                .body(body).canCloseWithEscape(true).build()).type(DialogType.multiAction(a,null,Math.min(2,a.size())))));
    }

    private void banConfirm(Player p,R r){
        ActionButton yes=ActionButton.builder(Component.text("CONFIRM BAN",NamedTextColor.RED)).width(150).action(DialogAction.staticAction(ClickEvent.runCommand("/sus ban "+r.uuid))).build();
        ActionButton no=ActionButton.builder(Component.text("Cancel",NamedTextColor.GRAY)).width(120).action(DialogAction.staticAction(ClickEvent.runCommand("/sus inspect "+r.uuid))).build();
        p.showDialog(Dialog.create(b->b.empty().base(DialogBase.builder(Component.text("CONFIRM BAN",NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .body(List.of(DialogBody.item(head(r.uuid),DialogBody.plainMessage(Component.text("Ban "+r.name+"?",NamedTextColor.WHITE)),false,true,360,72),
                        DialogBody.plainMessage(Component.text("This executes the configured server ban command.",NamedTextColor.GRAY))))
                .canCloseWithEscape(true).build()).type(DialogType.confirmation(yes,no))));
    }

    private void ban(Player p,UUID u){
        R r=get(u);if(r==null)return;
        String cmd=getConfig().getString("ban-command","ban {player} Suspicious activity").replace("{player}",r.name);
        if(cmd.startsWith("/"))cmd=cmd.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd);main(p);
    }

    private void clear(Player p,UUID u){
        try(PreparedStatement q=db.prepareStatement("DELETE FROM flags WHERE uuid=?")){q.setString(1,u.toString());q.executeUpdate();}catch(SQLException e){p.sendRichMessage("<red>Could not clear stats.</red>");}
        p.sendRichMessage("<green>ArcticSus stats cleared.</green>");main(p);
    }

    private ItemStack head(UUID u){
        ItemStack i=new ItemStack(Material.PLAYER_HEAD); OfflinePlayer op=Bukkit.getOfflinePlayer(u);
        var m=i.getItemMeta(); if(m!=null){m.displayName(Component.text(op.getName()==null?"Unknown":op.getName()));i.setItemMeta(m);} return i;
    }

    private List<R> list(){List<R> x=new ArrayList<>();if(db==null)return x;
        try(PreparedStatement p=db.prepareStatement("SELECT uuid,name,flags,last_flag FROM flags WHERE flags>=? ORDER BY flags DESC,last_flag DESC LIMIT ?")){
            p.setInt(1,Math.max(1,getConfig().getInt("minimum-flags",1)));p.setInt(2,Math.max(1,getConfig().getInt("max-players",18)));
            try(ResultSet q=p.executeQuery()){while(q.next())x.add(new R(UUID.fromString(q.getString(1)),q.getString(2),q.getInt(3),q.getLong(4)));}
        }catch(SQLException|IllegalArgumentException ignored){} return x;
    }

    private R get(UUID u){if(db==null)return null;try(PreparedStatement p=db.prepareStatement("SELECT uuid,name,flags,last_flag FROM flags WHERE uuid=?")){
        p.setString(1,u.toString());try(ResultSet q=p.executeQuery()){if(q.next())return new R(u,q.getString(2),q.getInt(3),q.getLong(4));}
    }catch(SQLException ignored){}return null;}

    private NamedTextColor color(int n){return n>=15?NamedTextColor.RED:n>=7?NamedTextColor.GOLD:NamedTextColor.YELLOW;}
    private String level(int n){return n>=15?"HIGH":n>=7?"MEDIUM":"LOW";}
    private String age(long t){long s=Math.max(0,(System.currentTimeMillis()-t)/1000);if(s<60)return s+"s ago";long m=s/60;if(m<60)return m+"m ago";long h=m/60;if(h<24)return h+"h ago";return h/24+"d ago";}
    private record R(UUID uuid,String name,int flags,long last){}
}
