using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net.NetworkInformation;
using System.Text.RegularExpressions;
using System.Windows.Forms;

namespace Otono {
    internal sealed class JavaInfo {
        public bool Ready;
        public string Message;
        static string Find(string name) {
            string home = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!String.IsNullOrEmpty(home) && File.Exists(Path.Combine(home, "bin", name))) return Path.Combine(home, "bin", name);
            foreach (string entry in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';')) {
                try { string file = Path.Combine(entry.Trim('"'), name); if (File.Exists(file)) return file; } catch (ArgumentException) { }
            }
            return null;
        }
        static int Version(string file) {
            if (file == null) return 0;
            using (Process p = new Process()) {
                p.StartInfo = new ProcessStartInfo(file, "-version") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
                p.Start();
                var output = p.StandardOutput.ReadToEndAsync(); var error = p.StandardError.ReadToEndAsync();
                if (!p.WaitForExit(4000)) { p.Kill(); return 0; }
                string text = output.Result + error.Result;
                Match m = Regex.Match(text, @"(?:javac\s+|version\s+""?)(\d+)(?:\.(\d+))?");
                if (!m.Success) return 0;
                int major = Int32.Parse(m.Groups[1].Value);
                return major == 1 && m.Groups[2].Success ? Int32.Parse(m.Groups[2].Value) : major;
            }
        }
        public static JavaInfo Inspect() {
            try {
                int java = Version(Find("java.exe")), compiler = Version(Find("javac.exe"));
                bool ready = java >= 21 && compiler >= 21;
                return new JavaInfo { Ready = ready, Message = ready ? "Java " + java + " / JDK " + compiler + " found" : "Install a Java 21 JDK, then reopen this launcher. Set JAVA_HOME or add its bin folder to PATH." };
            } catch (Exception e) { return new JavaInfo { Ready = false, Message = "Java check failed: " + e.Message }; }
        }
    }
    internal sealed class Installation {
        public static readonly string[] Assets = { "MapleStory.exe", "Character.wz", "Item.wz", "Skill.wz", "Morph.wz", "Map.wz", "Mob.wz", "Npc.wz", "Reactor.wz", "Quest.wz", "String.wz", "Etc.wz", "Sound.wz", "UI.wz", "Base.wz" };
        public string GameDirectory;
        public string[] Missing;
        public bool GameReady, ClientReady, Built, BundleReady;
        public bool Ready { get { return GameReady && ClientReady && Built; } }
        public static Installation Inspect(string root) {
            string game = Path.Combine(root, "game");
            if (!File.Exists(Path.Combine(game, "MapleStory.exe")) && File.Exists(Path.Combine(game, "MapleStory", "MapleStory.exe"))) game = Path.Combine(game, "MapleStory");
            string[] missing = Assets.Where(f => !File.Exists(Path.Combine(game, f))).ToArray();
            return new Installation {
                GameDirectory = game, Missing = missing, GameReady = missing.Length == 0,
                ClientReady = File.Exists(Path.Combine(game, "Kinoko.exe")) && File.Exists(Path.Combine(game, "Kinoko.dll")),
                Built = File.Exists(Path.Combine(root, "server", "target", "server.jar")),
                BundleReady = File.Exists(Path.Combine(root, "client", "bin", "Kinoko.exe")) && File.Exists(Path.Combine(root, "client", "bin", "Kinoko.dll"))
            };
        }
    }
    internal sealed class Launcher : Form {
        readonly string root;
        JavaInfo java;
        Installation install;
        readonly Label javaText, gameText, setupText, portText, nextText;
        readonly Button setupButton, serverButton, gameButton;
        readonly Timer timer = new Timer();
        readonly Dictionary<Label,Label> checkMarks = new Dictionary<Label,Label>();
        TableLayoutPanel actions;
        TableLayoutPanel layout, heading, body, checks;
        Image wallpaper;
        bool wallpaperTheme;
        bool? arrangedReady;
        Process setupProcess, serverProcess;
        bool gameLaunching;
        readonly Color ink = Color.FromArgb(34, 48, 60), muted = Color.FromArgb(88, 99, 108), accent = Color.FromArgb(168, 75, 34);
        static Label TextLabel(string text, int size, Color color, bool bold) {
            return new Label { BackColor = Color.Transparent, Text = text, AutoSize = false, Dock = DockStyle.Fill, ForeColor = color, Font = new Font("Segoe UI", size, bold ? FontStyle.Bold : FontStyle.Regular), TextAlign = ContentAlignment.MiddleLeft };
        }
        Button ButtonFor(string text, EventHandler action, bool primary) {
            var b = new Button { Text = text, Dock = DockStyle.Fill, Height = 44, Margin = new Padding(0, 5, 0, 5), FlatStyle = FlatStyle.Flat, Font = new Font("Segoe UI", 11, FontStyle.Bold), BackColor = primary ? accent : Color.White, ForeColor = primary ? Color.White : ink, Cursor = Cursors.Hand };
            b.FlatAppearance.BorderColor = primary ? accent : Color.FromArgb(205, 211, 214); b.Click += action; return b;
        }
        static bool Active(Process p) { try { return p != null && !p.HasExited; } catch { return false; } }
        static bool PortBusy(int port) { try { return IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners().Any(p => p.Port == port); } catch { return true; } }
        public Launcher(string directory, JavaInfo info) {
            root = directory; java = info;
            Text = "Otono v95"; ClientSize = new Size(850, 610); MinimumSize = new Size(866, 649);
            StartPosition = FormStartPosition.CenterScreen; AutoScaleMode = AutoScaleMode.Dpi;
            BackColor = Color.FromArgb(246, 245, 241); Font = new Font("Segoe UI", 10); Icon = SystemIcons.Application;
            layout = new TableLayoutPanel { BackColor = Color.Transparent, Dock = DockStyle.Fill, Padding = new Padding(28, 20, 28, 12), ColumnCount = 1, RowCount = 3 };
            layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 84)); layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100)); layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 38)); Controls.Add(layout);
            heading = new TableLayoutPanel { BackColor = Color.Transparent, Dock = DockStyle.Fill, RowCount = 2, ColumnCount = 1, Margin = Padding.Empty };
            heading.RowStyles.Add(new RowStyle(SizeType.Absolute, 48)); heading.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            heading.Controls.Add(TextLabel("Otono v95", 28, ink, true)); heading.Controls.Add(TextLabel("Your local MapleStory server", 11, muted, false)); layout.Controls.Add(heading, 0, 0);
            body = new TableLayoutPanel { BackColor = Color.Transparent, Dock = DockStyle.Fill, ColumnCount = 2, RowCount = 1, Margin = new Padding(0, 12, 0, 0) };
            body.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 47)); body.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 53)); layout.Controls.Add(body, 0, 1);
            checks = new TableLayoutPanel { BackColor = Color.Transparent, Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 6, Margin = new Padding(0, 0, 24, 0) };
            checks.RowStyles.Add(new RowStyle(SizeType.Absolute, 32)); for(int i=0;i<3;i++) checks.RowStyles.Add(new RowStyle(SizeType.Absolute, 88)); checks.RowStyles.Add(new RowStyle(SizeType.Percent, 100)); checks.RowStyles.Add(new RowStyle(SizeType.Absolute, 46));
            checks.Controls.Add(TextLabel("Ready to play?", 15, ink, true));
            javaText = AddCheck(checks, "1   Java 21 JDK", 1); gameText = AddCheck(checks, "2   MapleStory v95 game files", 2); setupText = AddCheck(checks, "3   Otono server and client", 3);
            checks.Controls.Add(TextLabel("Install your v95 game with the game folder button below.\r\nThe installer's game\\MapleStory subfolder works too. Your game is not downloaded by this launcher.", 10, Color.Black, false), 0, 4);
            var openGame = ButtonFor("Open game folder", delegate { OpenFolder(Path.Combine(root, "game")); }, false); openGame.Font = new Font("Segoe UI", 9); var checklistActions = new TableLayoutPanel { BackColor = Color.Transparent, ColumnCount = 2, RowCount = 1, Dock = DockStyle.Fill, Margin = Padding.Empty };
            checklistActions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
            checklistActions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
            var refresh = ButtonFor("Refresh checks", delegate { java=JavaInfo.Inspect(); RefreshState(); }, false);
            refresh.Font = openGame.Font;
            checklistActions.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            openGame.Margin = new Padding(0, 3, 3, 3);
            refresh.Margin = new Padding(3, 3, 0, 3);
            checklistActions.Controls.Add(openGame, 0, 0);
            checklistActions.Controls.Add(refresh, 1, 0);
            checks.Controls.Add(checklistActions, 0, 5); body.Controls.Add(checks, 0, 0);
            actions = new TableLayoutPanel { Dock = DockStyle.Fill, BackColor = Color.White, Padding = new Padding(20), ColumnCount = 1, RowCount = 7, Margin = Padding.Empty };
            actions.RowStyles.Add(new RowStyle(SizeType.Absolute, 34));actions.RowStyles.Add(new RowStyle(SizeType.Absolute, 92));
            for(int i=0;i<3;i++)actions.RowStyles.Add(new RowStyle(SizeType.Absolute, 54)); actions.RowStyles.Add(new RowStyle(SizeType.Percent,100)); actions.RowStyles.Add(new RowStyle(SizeType.Absolute,36));
            actions.Controls.Add(TextLabel("Let's get started",15,ink,true)); nextText = TextLabel("",10,Color.Black,false);actions.Controls.Add(nextText);
            setupButton=ButtonFor("Initial Setup", Setup, true);serverButton=ButtonFor("Start Server", StartServer, false);gameButton=ButtonFor("Start Game", StartGame, false);
            actions.Controls.Add(setupButton); actions.Controls.Add(serverButton); actions.Controls.Add(gameButton);portText=TextLabel("",10,Color.Black,false);actions.Controls.Add(portText);
            var links=new FlowLayoutPanel { BackColor=Color.Transparent,Dock=DockStyle.Fill,Margin=Padding.Empty };
            foreach(string name in new[]{"Config","Documentation"}) {var link=new LinkLabel{Text=name,AutoSize=true,Margin=new Padding(0,10,18,0),LinkColor=accent};string choice=name;link.LinkClicked+=delegate{OpenPath(Path.Combine(root,choice=="Config"?"config.yaml":"docs"));};links.Controls.Add(link);}
            var guide = new LinkLabel { Text = "Launcher Guide", AutoSize = true, Margin = new Padding(0,10,18,0), LinkColor = accent };
            guide.LinkClicked += delegate {
                MessageBox.Show(this,
                    "Refresh checks: reruns the checklist and refreshes the server status.\r\n\r\n" +
                    "Open game folder: shows where to put the compatible game files.\r\n\r\n" +
                    "Initial Setup: builds the server and installs the bundled client files.\r\n\r\n" +
                    "Repair Setup: restores the bundled client files and builds the server if the JAR is missing. Both setup options preserve saved characters.\r\n\r\n" +
                    "Start Server: starts the local server.\r\n\r\n" +
                    "Start Game: opens the client once the server is ready.\r\n\r\n" +
                    "Config: opens the server settings.\r\n\r\n" +
                    "Documentation: opens the project docs.",
                    "Launcher Guide", MessageBoxButtons.OK, MessageBoxIcon.Information);
            };
            links.Controls.Add(guide);
            actions.Controls.Add(links);body.Controls.Add(actions,1,0);
            layout.Controls.Add(TextLabel("Closing this launcher leaves a running server open.\r\nClose its server window to stop it.",9,Color.Black,false),0,2);

            ApplyWallpaper();
            serverProcess=RestoreSession("server");setupProcess=RestoreSession("setup");
            timer.Interval=2000;timer.Tick+=delegate{CheckSetupExit();RefreshState();};timer.Start(); RefreshState();
        }
        Label AddCheck(TableLayoutPanel parent,string title,int row) {
            var card=new TableLayoutPanel{BackColor=Color.Transparent,Dock=DockStyle.Fill,ColumnCount=2,RowCount=2,Margin=new Padding(0,3,0,3)};
            card.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,30));card.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            card.RowStyles.Add(new RowStyle(SizeType.Absolute,26));card.RowStyles.Add(new RowStyle(SizeType.Percent,100));
            var mark=TextLabel("",19,muted,true);mark.Font=new Font("Segoe UI Symbol",14);mark.Margin=Padding.Empty;mark.TextAlign=ContentAlignment.MiddleCenter;card.Controls.Add(mark,0,0);
            card.Controls.Add(TextLabel(title,11,ink,true),1,0);var text=TextLabel("Checking...",9,Color.Black,false);card.Controls.Add(text,1,1);
            checkMarks.Add(text,mark);parent.Controls.Add(card,0,row);return text;
        }
        protected override void OnPaintBackground(PaintEventArgs e){
            if(!wallpaperTheme||wallpaper==null){base.OnPaintBackground(e);return;}
            e.Graphics.DrawImage(wallpaper, ClientRectangle);
        }

        void ApplyWallpaper(){
            string file = Path.Combine(root, "launcher", "assets", "maplestory_wallpaper_01_1600x1200.jpg");
            if (wallpaper == null && File.Exists(file)) {
                try {
                    using (var source = Image.FromFile(file)) wallpaper = new Bitmap(source);
                } catch (ArgumentException) { }
                catch (IOException) { }
            }
            wallpaperTheme=wallpaper!=null;SuspendLayout();
            BackgroundImage=wallpaperTheme?wallpaper:null;BackgroundImageLayout=ImageLayout.Zoom;
            BackColor=wallpaperTheme?Color.FromArgb(232,244,252):Color.FromArgb(246,245,241);
            ClientSize=wallpaperTheme?new Size(1000,760):new Size(850,610);
            layout.RowStyles[1].SizeType=wallpaperTheme?SizeType.Absolute:SizeType.Percent;layout.RowStyles[1].Height=wallpaperTheme?380:100;
            layout.RowStyles[2].SizeType=wallpaperTheme?SizeType.Percent:SizeType.Absolute;layout.RowStyles[2].Height=wallpaperTheme?100:38;
            ((Label)layout.GetControlFromPosition(0,2)).TextAlign=wallpaperTheme?ContentAlignment.TopLeft:ContentAlignment.MiddleLeft;
            heading.Controls[0].ForeColor=wallpaperTheme?Color.White:ink;heading.Controls[1].ForeColor=wallpaperTheme?Color.White:muted;
            MinimumSize = wallpaperTheme ? new Size(866, 730) : new Size(866, 649);
            actions.Dock = DockStyle.Fill;
            actions.Height = 360;
            actions.Margin = wallpaperTheme ? new Padding(146, 0, 0, 0) : Padding.Empty;
            actions.Padding=wallpaperTheme?new Padding(16):new Padding(20);
            actions.RowStyles[0].Height=wallpaperTheme?30:34;actions.RowStyles[1].Height=wallpaperTheme?60:92;
            actions.RowStyles[6].Height=wallpaperTheme?32:36;
            arrangedReady=null;

            heading.Padding=wallpaperTheme?new Padding(195,0,0,0):Padding.Empty;
            checks.BackColor=wallpaperTheme?Color.FromArgb(217,255,255,255):Color.Transparent;
            for (int row = 1; row <= 3; row++) checks.RowStyles[row].Height = wallpaperTheme ? 66 : 88;
            checks.Padding=wallpaperTheme?new Padding(14,8,14,8):Padding.Empty;
            actions.BackColor=wallpaperTheme?Color.FromArgb(217,255,255,255):Color.White;
            ResumeLayout(true);Invalidate(true);
        }
        void Mark(Label detail,bool ready){var mark=checkMarks[detail];mark.Text=ready?"\u2713":"\u2717";mark.ForeColor=ready?Color.FromArgb(43,116,74):Color.FromArgb(174,51,46);}
        void ArrangeActions(bool ready){
            if(arrangedReady==ready)return;arrangedReady=ready;
            actions.SuspendLayout();
            actions.SetCellPosition(setupButton,new TableLayoutPanelCellPosition(0,ready?5:2));
            actions.SetCellPosition(serverButton,new TableLayoutPanelCellPosition(0,ready?2:3));
            actions.SetCellPosition(gameButton,new TableLayoutPanelCellPosition(0,ready?3:4));
            actions.SetCellPosition(portText,new TableLayoutPanelCellPosition(0,ready?4:5));
            for(int row=2;row<=5;row++){actions.RowStyles[row].SizeType=row==(ready?4:5)?SizeType.Percent:SizeType.Absolute;actions.RowStyles[row].Height=row==(ready?4:5)?100:row==5&&ready?40:54;}
            setupButton.Font=new Font("Segoe UI",ready?9:11,FontStyle.Bold);
            setupButton.BackColor=ready?Color.White:accent;setupButton.ForeColor=ready?ink:Color.White;
            serverButton.BackColor=ready?accent:Color.White;serverButton.ForeColor=ready?Color.White:ink;
            setupButton.FlatAppearance.BorderColor=ready?Color.FromArgb(205,211,214):accent;serverButton.FlatAppearance.BorderColor=ready?accent:Color.FromArgb(205,211,214);
            actions.ResumeLayout();
        }
        void RefreshState() {
            install=Installation.Inspect(root); bool settingUp=Active(setupProcess), serving=Active(serverProcess), login=PortBusy(8484), channel=PortBusy(8585);
            Mark(javaText,java.Ready);Mark(gameText,install.GameReady);Mark(setupText,install.Built&&install.ClientReady&&install.BundleReady);
            ArrangeActions(install.Ready);
            javaText.Text=java.Message;
            gameText.Text=install.GameReady?"Found in "+(install.GameDirectory.EndsWith("MapleStory",StringComparison.OrdinalIgnoreCase)?"game\\MapleStory":"game"):"Missing: "+String.Join(", ",install.Missing.Take(5))+(install.Missing.Length>5?" ...":"")+". Install the compatible v95 game into game or game\\MapleStory.";
            setupText.Text=!install.BundleReady?"Bundled client files are missing. Restore client\\bin from your Otono download.":install.Ready?"Server build and client files are ready.":!install.Built?"Run Initial Setup to build the server and install the Kinoko client.":"Kinoko files need restoring. Run Repair Setup.";
            setupButton.Text=settingUp?"Setup running...":install.Ready?"Repair Setup":"Initial Setup";
            setupButton.Enabled=java.Ready&&install.GameReady&&install.BundleReady&&!settingUp&&!serving&&!login&&!channel;
            serverButton.Enabled=java.Ready&&install.Ready&&!settingUp&&!serving&&!login&&!channel;
            gameButton.Enabled=java.Ready&&install.Ready&&!settingUp&&!gameLaunching&&serving&&login&&channel;
            gameButton.BackColor = gameButton.Enabled ? Color.FromArgb(38, 112, 69) : Color.FromArgb(232, 231, 227);
            gameButton.ForeColor = gameButton.Enabled ? Color.White : muted;
            gameButton.Cursor = gameButton.Enabled ? Cursors.Hand : Cursors.Default;
            gameButton.Text = gameLaunching ? "Opening game..." : gameButton.Enabled ? "Start Game (Ready)" : "Start Game";
            serverButton.Text=serving?"Server running":"Start Server";
            if(settingUp)nextText.Text="Setup is running in its own window. The first build downloads Maven and dependencies. Keep that window open.";
            else if(!java.Ready)nextText.Text="Install a Java 21 JDK, then reopen the launcher. Maven and SQLite need no separate installation.";
            else if(!install.GameReady)nextText.Text="Install your game files into game, then refresh these checks.";
            else if(!install.Ready)nextText.Text="Click Initial Setup to prepare Otono. Keep your internet connection on during first setup.";
            else if(serving)nextText.Text=login&&channel?"The server is listening. You can start the game now.":"The server is starting. Watch its window for progress.";
            else if(login||channel)nextText.Text="A server is already using Otono's ports. Close it, then start Otono v95 here.";
            else nextText.Text="Start the server first, then open the game. SQLite initializes automatically on first server startup.";
            portText.Text=serving?(login&&channel?"Server: online":"Server: starting"):(login||channel?"Server: ports already in use":"Server: offline");
        }
        Process RunScript(string name,string arguments) { return LaunchScript(root,name,arguments,ProcessWindowStyle.Normal); }
        internal static Process LaunchScript(string root,string name,string arguments,ProcessWindowStyle style) {
            string file=Path.Combine(root,"launcher",name);if(!File.Exists(file))throw new FileNotFoundException("Restore the missing Otono script: "+name);
            return Process.Start(new ProcessStartInfo(Environment.GetEnvironmentVariable("COMSPEC")??"cmd.exe","/d /s /c \"\""+file+"\" "+arguments+"\""){WorkingDirectory=root,UseShellExecute=true,WindowStyle=style});
        }
        void Setup(object sender,EventArgs e){try{setupProcess=RunScript("Initial-Server-Setup.cmd","--no-pause");SaveSession(setupProcess,"setup");RefreshState();}catch(Exception x){Error(x);}}
        void CheckSetupExit(){if(setupProcess==null||Active(setupProcess))return;int code=setupProcess.ExitCode;setupProcess.Dispose();setupProcess=null;RefreshState();if(code!=0)MessageBox.Show(this,"Setup did not complete. Run launcher\\Initial-Server-Setup.cmd directly to read the build error, correct it, then retry. Your saved database is not reset.","Setup needs attention",MessageBoxButtons.OK,MessageBoxIcon.Warning);}
        void StartServer(object sender,EventArgs e){RefreshState();if(!serverButton.Enabled)return;try{serverProcess=RunScript("Start-Server.cmd","--no-pause");
            SaveSession(serverProcess,"server");RefreshState();}catch(Exception x){Error(x);}}
        void SaveSession(Process p,string name){File.WriteAllText(Path.Combine(root,"launcher","bin",name+"-session.txt"),p.Id+"|"+p.StartTime.ToUniversalTime().Ticks);}
        Process RestoreSession(string name){
            try{string file=Path.Combine(root,"launcher","bin",name+"-session.txt");if(!File.Exists(file))return null;string[] data=File.ReadAllText(file).Split('|');var p=Process.GetProcessById(Int32.Parse(data[0]));if(!p.HasExited&&p.StartTime.ToUniversalTime().Ticks==Int64.Parse(data[1])&&p.ProcessName.Equals("cmd",StringComparison.OrdinalIgnoreCase))return p;p.Dispose();}catch{ }return null;
        }
        void StartGame(object sender,EventArgs e){
            RefreshState();if(!gameButton.Enabled)return;gameLaunching=true;RefreshState();
            try{
                var process=RunScript("Start-Game.cmd","--no-pause");
                var monitor=new Timer{Interval=100};monitor.Tick+=delegate{
                    if(IsDisposed){monitor.Stop();monitor.Dispose();process.Dispose();return;}
                    if(!process.HasExited)return;
                    int code=process.ExitCode;monitor.Stop();monitor.Dispose();process.Dispose();
                    if(code==0){Close();return;}
                    gameLaunching=false;RefreshState();MessageBox.Show(this,"The game could not be launched. Check the game files and try again.","Otono",MessageBoxButtons.OK,MessageBoxIcon.Warning);
                };monitor.Start();
            }catch(Exception x){gameLaunching=false;RefreshState();Error(x);}
        }

        void OpenFolder(string path){try{Directory.CreateDirectory(path);OpenPath(path);}catch(Exception x){Error(x);}}
        void OpenPath(string path){try{Process.Start(new ProcessStartInfo(path){UseShellExecute=true});}catch(Exception x){Error(x);}}
        void Error(Exception error){MessageBox.Show(this,error.Message,"Otono",MessageBoxButtons.OK,MessageBoxIcon.Warning);}
        protected override void Dispose(bool disposing){if(disposing){timer.Dispose();BackgroundImage=null;if(wallpaper!=null)wallpaper.Dispose();}base.Dispose(disposing);}
    }
    internal static class Program {
        [STAThread] static int Main(string[] args) {
            try {
                string root=Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"..",".."));
                if(args.Length>0&&args[0]=="--self-test")return SelfTest();
                Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
                JavaInfo java=JavaInfo.Inspect();
                using(var form=new Launcher(root,java)) {
                    if(args.Length==2&&args[0]=="--preview"){form.Show();Application.DoEvents();using(var bmp=new Bitmap(form.Width,form.Height)){form.DrawToBitmap(bmp,new Rectangle(Point.Empty,form.Size));bmp.Save(args[1]);}form.Close();return 0;}
                    Application.Run(form);
                }return 0;
            } catch(Exception e){MessageBox.Show(e.Message,"Otono launcher",MessageBoxButtons.OK,MessageBoxIcon.Error);return 1;}
        }
        static int SelfTest(){
            string root=Path.Combine(Path.GetTempPath(),"Otono launcher test & "+Guid.NewGuid().ToString("N"));
            try{
                Directory.CreateDirectory(root);var initial=Installation.Inspect(root);Require(!initial.Ready&&!initial.GameReady,"Empty install");
                string game=Path.Combine(root,"game","MapleStory");Directory.CreateDirectory(game);foreach(string name in Installation.Assets)File.WriteAllText(Path.Combine(game,name),"");
                var assets=Installation.Inspect(root);Require(assets.GameReady&&assets.GameDirectory==game&&!assets.ClientReady,"Nested installer folder");
                Directory.CreateDirectory(Path.Combine(root,"server","target"));File.WriteAllText(Path.Combine(root,"server","target","server.jar"),"");
                Directory.CreateDirectory(Path.Combine(root,"client","bin"));foreach(string file in new[]{"Kinoko.exe","Kinoko.dll"})File.WriteAllText(Path.Combine(root,"client","bin",file),"");
                Require(Installation.Inspect(root).BundleReady&&!Installation.Inspect(root).Ready,"Repair required despite existing jar");
                foreach(string file in new[]{"Kinoko.exe","Kinoko.dll"})File.WriteAllText(Path.Combine(game,file),"");Require(Installation.Inspect(root).Ready,"Prepared install");
                File.Delete(Path.Combine(game,"Map.wz"));Require(!Installation.Inspect(root).Ready,"Missing required asset");
                string direct=Path.Combine(root,"game");foreach(string name in Installation.Assets)File.WriteAllText(Path.Combine(direct,name),"");foreach(string name in new[]{"Kinoko.exe","Kinoko.dll"})File.WriteAllText(Path.Combine(direct,name),"");
                Require(Installation.Inspect(root).Ready&&Installation.Inspect(root).GameDirectory==direct,"Direct installation has matching script precedence");
                Directory.CreateDirectory(Path.Combine(root,"launcher"));
                File.WriteAllText(Path.Combine(root,"launcher","Start-Server.cmd"),"@echo off\r\n> \"%~dp0invoked.txt\" echo %~1\r\nexit /b 0\r\n");
                using(var invoked=Launcher.LaunchScript(root,"Start-Server.cmd","--no-pause",ProcessWindowStyle.Hidden)){Require(invoked.WaitForExit(5000)&&invoked.ExitCode==0,"Script invocation");}
                Require(File.ReadAllText(Path.Combine(root,"launcher","invoked.txt")).Trim()=="--no-pause","Script path spaces/ampersand and arguments");
                return 0;
            }finally{Directory.Delete(root,true);}
        }
        static void Require(bool value,string message){if(!value)throw new Exception("Launcher test failed: "+message);}
    }
}
