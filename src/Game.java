import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.GameCanvas;

public class Game extends MIDlet {
    private GameScreen screen;
    public void startApp() {
        if (screen == null) {
            screen = new GameScreen();
            Display.getDisplay(this).setCurrent(screen);
            new Thread(screen).start();
        }
    }
    public void pauseApp() {}
    public void destroyApp(boolean u) { if (screen != null) screen.stop(); }
}

class GameScreen extends GameCanvas implements Runnable {
    static final int TITLE=0, PLAY=1, DEAD=2, WIN=3;
    static final int MODE_FLY=0, MODE_CAR=1;
    static final int T_SOLDIER=0, T_CAR=1, T_TANK=2, T_HELI=3;
    static final int MAXEN=6, MAXEB=24, MAXPB=8, MAXSMOKE=16;
    static final int TOTAL_POOL=20, KILL_TARGET=16;

    boolean running = true;
    int W, H, groundY, maxAlt;
    int state = TITLE, frame, deadTimer;

    Image heliLevel, heliDown, heliUp, carImg, bgImg, soldierImg, armorImg, tankImg, heliEnemyImg, bossImg;
    int vw=56, vh=34, bgW, bgH;

    int mode = MODE_FLY;
    int wx, py, php=100;
    int cam, tilt, hitFlash, fireCd;
    boolean k7,k9,k1,k3;

    int bombCd=0; boolean bombOn=false; int bombX,bombY;
    boolean explOn=false; int explX,explY,explTimer;

    int[] smX=new int[MAXSMOKE], smY=new int[MAXSMOKE], smAge=new int[MAXSMOKE]; int smNext=0;

    int[] pbX=new int[MAXPB], pbY=new int[MAXPB], pbDX=new int[MAXPB], pbDY=new int[MAXPB];
    boolean[] pbOn=new boolean[MAXPB];

    int[] ebX=new int[MAXEB], ebY=new int[MAXEB], ebDX=new int[MAXEB], ebDY=new int[MAXEB];
    boolean[] ebOn=new boolean[MAXEB]; boolean[] ebHeavy=new boolean[MAXEB];

    int[] enX=new int[MAXEN], enY=new int[MAXEN], enHP=new int[MAXEN], enType=new int[MAXEN];
    boolean[] enOn=new boolean[MAXEN];
    int[] enState=new int[MAXEN]; // 0 guard, 1 chase
    boolean[] enStub=new boolean[MAXEN];
    int[] enTimer=new int[MAXEN]; boolean[] enBurst=new boolean[MAXEN];
    int[] enRecoil=new int[MAXEN];
    int spawnCd=0, totalSpawned=0, totalKilled=0;

    boolean bossSpawned=false, bossOn=false;
    int bossX, bossY, bossHP, bossTimer; boolean bossBurst; int bossRecoil;
    int bossW=48, bossH=56;

    int HOME_END = 150;

    GameScreen() {
        super(true);
        W=getWidth(); H=getHeight();
        groundY=H-40; maxAlt=groundY-140; if(maxAlt<20) maxAlt=20;
        wx=60; py=H/2;

        try {
            Image base=scale(autoCrop(Image.createImage("/heli.png")),vw,vh);
            heliLevel=base; heliDown=skew(base,6); heliUp=skew(base,-6);
        } catch(Exception e){ heliLevel=heliDown=heliUp=null; }
        try { carImg=scale(autoCrop(Image.createImage("/car.png")),vw,vh);} catch(Exception e){ carImg=null; }
        try { soldierImg=scale(autoCrop(Image.createImage("/soldier.png")),24,32);} catch(Exception e){ soldierImg=null; }
        try { armorImg=scale(autoCrop(Image.createImage("/armorcar.png")),36,32);} catch(Exception e){ armorImg=null; }
        try { tankImg=scale(autoCrop(Image.createImage("/tank.png")),42,34);} catch(Exception e){ tankImg=null; }
        try { heliEnemyImg=scale(autoCrop(Image.createImage("/enemyheli.png")),50,30);} catch(Exception e){ heliEnemyImg=null; }
        try { bossImg=scale(autoCrop(Image.createImage("/boss.png")),bossW,bossH);} catch(Exception e){ bossImg=null; }
        try {
            Image rawBg=Image.createImage("/bg.png");
            bgH=H; bgW=rawBg.getWidth()*bgH/rawBg.getHeight();
            bgImg=fade(scale(rawBg,bgW,bgH),40);
        } catch(Exception e){ bgImg=null; }

        for(int i=0;i<MAXSMOKE;i++) smAge[i]=0;
        for(int i=0;i<MAXEN;i++) enOn[i]=false;
    }

    protected void keyPressed(int kc){
        if(kc==KEY_NUM7) k7=true; else if(kc==KEY_NUM9) k9=true;
        else if(kc==KEY_NUM1) k1=true; else if(kc==KEY_NUM3) k3=true;
        else if(kc==KEY_POUND){
            if(mode==MODE_FLY && bombCd==0 && !bombOn && state==PLAY){
                bombOn=true; bombX=wx+vw/2; bombY=py; bombCd=250;
            }
        }
    }
    protected void keyReleased(int kc){
        if(kc==KEY_NUM7) k7=false; else if(kc==KEY_NUM9) k9=false;
        else if(kc==KEY_NUM1) k1=false; else if(kc==KEY_NUM3) k3=false;
    }

    Image autoCrop(Image src){
        int sw=src.getWidth(), sh=src.getHeight();
        int[] px=new int[sw*sh]; src.getRGB(px,0,sw,0,0,sw,sh);
        int minX=sw,minY=sh,maxX=-1,maxY=-1;
        for(int y=0;y<sh;y++) for(int x=0;x<sw;x++)
            if((px[y*sw+x]>>>24)>20){ if(x<minX)minX=x; if(x>maxX)maxX=x; if(y<minY)minY=y; if(y>maxY)maxY=y; }
        if(maxX<0) return src;
        int nw=maxX-minX+1, nh=maxY-minY+1;
        int[] crop=new int[nw*nh];
        for(int y=0;y<nh;y++) for(int x=0;x<nw;x++) crop[y*nw+x]=px[(minY+y)*sw+(minX+x)];
        return Image.createRGBImage(crop,nw,nh,true);
    }
    Image scale(Image src,int nw,int nh){
        int sw=src.getWidth(), sh=src.getHeight();
        int[] sp=new int[sw*sh]; src.getRGB(sp,0,sw,0,0,sw,sh);
        int[] dst=new int[nw*nh];
        for(int y=0;y<nh;y++){ int sy=y*sh/nh;
            for(int x=0;x<nw;x++){ int sx=x*sw/nw; dst[y*nw+x]=sp[sy*sw+sx]; } }
        return Image.createRGBImage(dst,nw,nh,true);
    }
    Image skew(Image src,int amount){
        int w=src.getWidth(), h=src.getHeight();
        int[] sp=new int[w*h]; src.getRGB(sp,0,w,0,0,w,h);
        int[] dst=new int[w*h];
        for(int y=0;y<h;y++){ int shift=((y*amount)/h)-(amount/2);
            for(int x=0;x<w;x++){ int sx=x-shift; int val=0; if(sx>=0&&sx<w) val=sp[y*w+sx]; dst[y*w+x]=val; } }
        return Image.createRGBImage(dst,w,h,true);
    }
    Image fade(Image src,int pct){
        int w=src.getWidth(), h=src.getHeight();
        int[] px=new int[w*h]; src.getRGB(px,0,w,0,0,w,h);
        int hr=0xB8,hg=0xC6,hb=0xD2;
        for(int i=0;i<px.length;i++){
            int a=px[i]&0xFF000000;
            int r=(px[i]>>16)&0xFF, gg=(px[i]>>8)&0xFF, b=px[i]&0xFF;
            r=r+(hr-r)*pct/100; gg=gg+(hg-gg)*pct/100; b=b+(hb-b)*pct/100;
            px[i]=a|(r<<16)|(gg<<8)|b;
        }
        return Image.createRGBImage(px,w,h,true);
    }

    public void stop(){ running=false; }

    public void run(){
        Graphics g=getGraphics();
        g.setFont(Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD,Font.SIZE_SMALL));
        while(running){
            long t0=System.currentTimeMillis();
            update(); draw(g); flushGraphics();
            long dt=System.currentTimeMillis()-t0;
            if(dt<40){ try{ Thread.sleep(40-dt);}catch(InterruptedException e){} }
        }
    }

    void resetGame(){
        wx=60; py=H/2; mode=MODE_FLY; php=100;
        for(int i=0;i<MAXEN;i++) enOn[i]=false;
        for(int i=0;i<MAXPB;i++) pbOn[i]=false;
        for(int i=0;i<MAXEB;i++) ebOn[i]=false;
        for(int i=0;i<MAXSMOKE;i++) smAge[i]=0;
        spawnCd=0; totalSpawned=0; totalKilled=0;
        bossSpawned=false; bossOn=false; bombOn=false; bombCd=0; explOn=false;
        state=PLAY;
    }

    void update(){
        frame++;
        int k=getKeyStates();

        if(state==TITLE){ if((k&FIRE_PRESSED)!=0) resetGame(); return; }
        if(state==DEAD || state==WIN){
            deadTimer++;
            if(deadTimer>25 && (k&FIRE_PRESSED)!=0) resetGame();
            return;
        }

        boolean left=(k&LEFT_PRESSED)!=0, right=(k&RIGHT_PRESSED)!=0;
        boolean up=(k&UP_PRESSED)!=0, down=(k&DOWN_PRESSED)!=0;

        if(left) wx-=3;
        if(right) wx+=3;

        tilt=0;
        if(mode==MODE_FLY){
            if(up){ py-=3; tilt=-1; }
            if(down){ py+=3; tilt=1; }
            if(right) tilt=1;
            if(left && !right) tilt=-1;
            if(k7){ wx-=3; py-=3; tilt=-1; }
            if(k9){ wx+=3; py-=3; tilt=1; }
            if(k1){ wx-=3; py+=3; tilt=-1; }
            if(k3){ wx+=3; py+=3; tilt=1; }
            if(py<maxAlt) py=maxAlt;
            if(py>=groundY){ py=groundY; mode=MODE_CAR; }
        } else {
            if(k7||k1) wx-=2;
            if(k9||k3) wx+=2;
            boolean moving=left||right||k7||k9||k1||k3;
            if(moving && frame%3==0){
                smX[smNext]=wx; smY[smNext]=groundY; smAge[smNext]=1;
                smNext=(smNext+1)%MAXSMOKE;
            }
            if(up){ mode=MODE_FLY; py=groundY-3; }
        }
        if(wx<0) wx=0;

        for(int i=0;i<MAXSMOKE;i++) if(smAge[i]>0){ smAge[i]++; if(smAge[i]>20) smAge[i]=0; }

        cam=wx-W/3; if(cam<0) cam=0;

        // home base healing
        if(wx<HOME_END && php<100 && frame%8==0) php++;

        // player gun - fully automatic
        if(fireCd>0) fireCd--;
        if(fireCd==0){
            fireCd=(mode==MODE_FLY)?9:7;
            for(int i=0;i<MAXPB;i++){
                if(!pbOn[i]){
                    pbOn[i]=true; pbX[i]=wx+vw/2;
                    pbY[i]=(mode==MODE_FLY)?py-vh/2:groundY-10;
                    pbDX[i]=(mode==MODE_FLY)?8:7;
                    pbDY[i]=(mode==MODE_FLY)?5:0;
                    break;
                }
            }
        }
        for(int i=0;i<MAXPB;i++) if(pbOn[i]){
            pbX[i]+=pbDX[i]; pbY[i]+=pbDY[i];
            if(pbX[i]-cam>W+20 || pbY[i]>H) pbOn[i]=false;
        }

        // bomb
        if(bombCd>0) bombCd--;
        if(bombOn){
            bombY+=6;
            if(bombY>=groundY){
                bombOn=false; explOn=true; explTimer=10; explX=bombX; explY=groundY;
                for(int i=0;i<MAXEN;i++) if(enOn[i] && Math.abs(enX[i]-bombX)<44){
                    enHP[i]-=70; if(enHP[i]<=0){ enOn[i]=false; totalKilled++; alertNearby(enX[i]); }
                }
                if(bossOn && Math.abs(bossX-bombX)<50){
                    bossHP-=70; if(bossHP<=0){ bossOn=false; state=WIN; deadTimer=0; }
                }
            }
        }
        if(explOn){ explTimer--; if(explTimer<=0) explOn=false; }

        // spawn pool
        if(spawnCd>0) spawnCd--;
        int count=0; for(int i=0;i<MAXEN;i++) if(enOn[i]) count++;
        if(!bossOn && totalSpawned<TOTAL_POOL && count<MAXEN && spawnCd==0){
            for(int i=0;i<MAXEN;i++){
                if(!enOn[i]){
                    enOn[i]=true;
                    int idx=totalSpawned;
                    int ty;
                    if(idx%7==6) ty=T_HELI;
                    else if(idx%5==4) ty=T_TANK;
                    else ty=(idx%2==0)?T_SOLDIER:T_CAR;
                    enType[i]=ty;
                    enX[i]=wx+W/2+40+(i*30);
                    enY[i]=(ty==T_HELI)?(maxAlt+30):groundY;
                    enHP[i]=(ty==T_SOLDIER)?20:(ty==T_CAR)?50:(ty==T_TANK)?80:25;
                    enState[i]=(ty==T_SOLDIER||ty==T_CAR)?((idx%5<2)?0:1):1;
                    enStub[i]=(ty==T_SOLDIER||ty==T_CAR)&&(idx%4==0);
                    enTimer[i]=30+(i*23)%100;
                    enBurst[i]=false; enRecoil[i]=0;
                    totalSpawned++;
                    break;
                }
            }
            spawnCd=55;
        }

        // boss trigger
        if(!bossSpawned && totalKilled>=KILL_TARGET){
            bossSpawned=true; bossOn=true;
            bossX=wx+W+60; bossY=groundY; bossHP=300; bossTimer=60; bossBurst=false; bossRecoil=0;
            for(int i=0;i<MAXEN;i++) if(enOn[i]){ enState[i]=1; enStub[i]=true; }
        }

        // update ground/flying enemies
        for(int i=0;i<MAXEN;i++){
            if(!enOn[i]) continue;
            int ty=enType[i];
            boolean flier=(ty==T_HELI);

            if(enState[i]==0){
                if(Math.abs(enX[i]-wx)<90) enState[i]=1;
            } else {
                int speed=(ty==T_CAR)?2:(ty==T_TANK)?1:(ty==T_HELI)?2:1;
                if(enX[i]>wx) enX[i]-=speed; else if(enX[i]<wx) enX[i]+=speed;
                if(flier){
                    if(enY[i]<py) enY[i]+=1; else if(enY[i]>py) enY[i]-=1;
                }
                if(!enStub[i] && (enX[i]-wx)>220) enState[i]=0;
            }

            if(enRecoil[i]>0) enRecoil[i]--;

            if(enState[i]==1){
                int onTime=(ty==T_TANK)?1:75, offTime=(ty==T_TANK)?100:125;
                enTimer[i]--;
                if(enTimer[i]<=0){
                    enBurst[i]=!enBurst[i];
                    enTimer[i]=enBurst[i]?onTime:offTime;
                }
                boolean canFire = flier ? (mode==MODE_FLY) : true;
                if(enBurst[i] && canFire && frame%(ty==T_TANK?1:12)==0){
                    for(int b=0;b<MAXEB;b++){
                        if(!ebOn[b]){
                            ebOn[b]=true;
                            int sy=flier?enY[i]:groundY-14;
                            ebX[b]=enX[i]; ebY[b]=sy;
                            enRecoil[i]=4;
                            if(flier){
                                ebDX[b]=(enX[i]>wx)?-6:6; ebDY[b]=0; ebHeavy[b]=false;
                            } else if(ty==T_TANK){
                                int tx=wx+vw/2, tyy=py-vh/2;
                                int ddx=tx-ebX[b], ddy=tyy-ebY[b];
                                int dist=(int)Math.sqrt(ddx*ddx+ddy*ddy); if(dist<1) dist=1;
                                ebDX[b]=ddx*3/dist; ebDY[b]=ddy*3/dist; ebHeavy[b]=true;
                            } else {
                                int tx=wx+vw/2, tyy=py-vh/2;
                                int ddx=tx-ebX[b], ddy=tyy-ebY[b];
                                int dist=(int)Math.sqrt(ddx*ddx+ddy*ddy); if(dist<1) dist=1;
                                int spd=(ty==T_CAR)?6:4;
                                ebDX[b]=ddx*spd/dist; ebDY[b]=ddy*spd/dist; ebHeavy[b]=false;
                            }
                            break;
                        }
                    }
                    if(ty==T_TANK) enBurst[i]=false;
                }
            }

            // ram (armor car close range, ground only, car mode)
            if(ty==T_CAR && mode==MODE_CAR && Math.abs(enX[i]-wx)<20 && hitFlash==0){
                php-=15; hitFlash=15;
                if(php<=0){ php=0; state=DEAD; deadTimer=0; }
            }

            // player bullet vs enemy
            for(int p=0;p<MAXPB;p++){
                if(pbOn[p]){
                    int ex=enX[i]-cam, psx=pbX[p]-cam;
                    int ew=(ty==T_SOLDIER)?24:(ty==T_CAR)?36:(ty==T_TANK)?42:50;
                    int eh=(ty==T_HELI)?30:32;
                    int topY = flier ? enY[i] : groundY-eh;
                    if(Math.abs(psx-ex)<ew/2 && pbY[p]>topY-4 && pbY[p]<topY+eh+4){
                        pbOn[p]=false; enHP[i]-=10;
                        if(enState[i]==0) enState[i]=1;
                        if(enHP[i]<=0){ enOn[i]=false; totalKilled++; alertNearby(enX[i]); }
                    }
                }
            }
        }

        // boss update
        if(bossOn){
            if(bossX>wx) bossX-=2; else if(bossX<wx) bossX+=2;
            if(bossRecoil>0) bossRecoil--;
            bossTimer--;
            if(bossTimer<=0){ bossBurst=!bossBurst; bossTimer=bossBurst?70:70; }
            if(bossBurst && frame%10==0){
                for(int b=0;b<MAXEB;b++){
                    if(!ebOn[b]){
                        ebOn[b]=true; ebX[b]=bossX; ebY[b]=groundY-20; bossRecoil=4;
                        int tx=wx+vw/2, tyy=py-vh/2;
                        int ddx=tx-ebX[b], ddy=tyy-ebY[b];
                        int dist=(int)Math.sqrt(ddx*ddx+ddy*ddy); if(dist<1) dist=1;
                        ebDX[b]=ddx*6/dist; ebDY[b]=ddy*6/dist; ebHeavy[b]=true;
                        break;
                    }
                }
            }
            if(Math.abs(bossX-wx)<24 && hitFlash==0){
                php-=20; hitFlash=15;
                if(php<=0){ php=0; state=DEAD; deadTimer=0; }
            }
            for(int p=0;p<MAXPB;p++){
                if(pbOn[p]){
                    int ex=bossX-cam, psx=pbX[p]-cam;
                    int topY=groundY-bossH;
                    if(Math.abs(psx-ex)<bossW/2 && pbY[p]>topY-4 && pbY[p]<topY+bossH+4){
                        pbOn[p]=false; bossHP-=8;
                        if(bossHP<=0){ bossOn=false; state=WIN; deadTimer=0; }
                    }
                }
            }
        }

        // enemy bullets
        for(int b=0;b<MAXEB;b++){
            if(ebOn[b]){
                ebX[b]+=ebDX[b]; ebY[b]+=ebDY[b];
                if(ebX[b]-cam<-20||ebX[b]-cam>W+20||ebY[b]<-20||ebY[b]>H+20){ ebOn[b]=false; continue; }
                int bsx=ebX[b]-cam, psx=wx-cam;
                boolean xHit=bsx>psx-2 && bsx<psx+vw+2;
                boolean yHit=ebY[b]>py-vh-2 && ebY[b]<py+4;
                if(xHit && yHit && hitFlash==0){
                    ebOn[b]=false;
                    php-=ebHeavy[b]?34:6;
                    hitFlash=12;
                    if(php<=0){ php=0; state=DEAD; deadTimer=0; }
                }
            }
        }
        if(hitFlash>0) hitFlash--;
    }

    void alertNearby(int deadX){
        for(int i=0;i<MAXEN;i++) if(enOn[i] && Math.abs(enX[i]-deadX)<80) enState[i]=1;
    }

    void draw(Graphics g){
        if(bgImg!=null && bgW>0){
            int off=cam%bgW;
            for(int x=-off;x<W;x+=bgW) g.drawImage(bgImg,x,0,Graphics.TOP|Graphics.LEFT);
        } else { g.setColor(0x87CEEB); g.fillRect(0,0,W,H); }

        if(state==TITLE){
            g.setColor(0x000000); g.fillRect(W/2-90,H/2-40,180,90);
            g.setColor(0xFFFFFF);
            g.drawString("WAHID FIRST GAME",W/2,H/2-34,Graphics.TOP|Graphics.HCENTER);
            if(((frame>>3)&1)==0) g.drawString("PRESS FIRE TO START",W/2,H/2-4,Graphics.TOP|Graphics.HCENTER);
            if(heliLevel!=null) g.drawImage(heliLevel,W/2-vw/2,H/2+20,Graphics.TOP|Graphics.LEFT);
            return;
        }

        int sx=wx-cam;

        // home base marker
        int hbsx=60-cam;
        if(hbsx>-40 && hbsx<W+40){
            g.setColor(0x5A3A1A); g.fillRect(hbsx-2,groundY-40,4,40);
            g.setColor(0xD32F2F); g.fillRect(hbsx+2,groundY-40,16,10);
            g.setColor(0x3E8E41); g.fillRect(hbsx-20,groundY-6,44,6);
            if(wx<HOME_END){ g.setColor(0x000000); g.drawString("HOME",hbsx-2,groundY-54,Graphics.TOP|Graphics.LEFT); }
        }

        for(int i=0;i<MAXSMOKE;i++) if(smAge[i]>0){
            int ssx=smX[i]-cam-smAge[i], ssy=smY[i]-smAge[i]/2, size=3+smAge[i]/3;
            g.setColor(0xAAAAAA); g.fillArc(ssx-size/2,ssy-size/2,size,size,0,360);
        }

        for(int i=0;i<MAXEN;i++){
            if(!enOn[i]) continue;
            int ty=enType[i];
            boolean flier=(ty==T_HELI);
            int ex=enX[i]-cam;
            Image eimg=(ty==T_SOLDIER)?soldierImg:(ty==T_CAR)?armorImg:(ty==T_TANK)?tankImg:heliEnemyImg;
            int ew=(ty==T_SOLDIER)?24:(ty==T_CAR)?36:(ty==T_TANK)?42:50;
            int eh=(ty==T_HELI)?30:32;
            int bob=(ty==T_SOLDIER && enState[i]==1)?((frame/6)%2==0?0:-2):0;
            int rec=(enRecoil[i]>0)?-2:0;
            int topY = flier ? enY[i] : groundY-eh;

            if(!flier){ g.setColor(0x111111); g.fillArc(ex-ew/2+3,groundY-5,ew-6,8,0,360); }
            if(eimg!=null){
                g.drawImage(eimg,ex-ew/2-1+rec,topY+bob,Graphics.TOP|Graphics.LEFT);
                g.drawImage(eimg,ex-ew/2+1+rec,topY+bob,Graphics.TOP|Graphics.LEFT);
                g.drawImage(eimg,ex-ew/2+rec,topY+bob,Graphics.TOP|Graphics.LEFT);
            } else {
                g.setColor(0x556B2F); g.fillRect(ex-ew/2,topY+bob,ew,eh);
            }
            g.setColor(0x000000); g.fillRect(ex-ew/2,topY-6,ew,4);
            g.setColor(enState[i]==0?0x777777:0xFFC107);
            int maxhp=(ty==T_SOLDIER)?20:(ty==T_CAR)?50:(ty==T_TANK)?80:25;
            g.fillRect(ex-ew/2+1,topY-5,(ew-2)*enHP[i]/maxhp,2);
        }

        if(bossOn){
            int bx=bossX-cam, topY=groundY-bossH; int rec=(bossRecoil>0)?-2:0;
            g.setColor(0x111111); g.fillArc(bx-bossW/2+4,groundY-6,bossW-8,10,0,360);
            if(bossImg!=null){
                g.drawImage(bossImg,bx-bossW/2-1+rec,topY,Graphics.TOP|Graphics.LEFT);
                g.drawImage(bossImg,bx-bossW/2+1+rec,topY,Graphics.TOP|Graphics.LEFT);
                g.drawImage(bossImg,bx-bossW/2+rec,topY,Graphics.TOP|Graphics.LEFT);
            } else { g.setColor(0x8B0000); g.fillRect(bx-bossW/2,topY,bossW,bossH); }
            g.setColor(0x000000); g.fillRect(bx-bossW/2,topY-8,bossW,5);
            g.setColor(0xD32F2F); g.fillRect(bx-bossW/2+1,topY-7,(bossW-2)*bossHP/300,3);
        }

        g.setColor(0xFF5555);
        for(int b=0;b<MAXEB;b++) if(ebOn[b]) g.fillRect(ebX[b]-cam-2,ebY[b],ebHeavy[b]?7:4,ebHeavy[b]?5:3);

        g.setColor(0xFFEB3B);
        for(int p=0;p<MAXPB;p++) if(pbOn[p]) g.fillRect(pbX[p]-cam,pbY[p],5,3);

        if(bombOn){ g.setColor(0x222222); g.fillArc(bombX-cam-4,bombY-4,8,8,0,360); }
        if(explOn){
            int t=10-explTimer;
            int r=6+t*4;
            g.setColor(t<2?0xFFFFFF:0xFF7700);
            g.fillArc(explX-cam-r/2,explY-r/2,r,r,0,360);
            g.setColor(0x333333);
            for(int a=0;a<8;a++){
                int ang=a*45;
                int dx=(int)(Math.cos(ang*Math.PI/180)*(r/2+4));
                int dy=(int)(Math.sin(ang*Math.PI/180)*(r/2+4));
                g.drawLine(explX-cam,explY,explX-cam+dx,explY+dy);
            }
        }

        Image img;
        if(mode==MODE_FLY) img=(tilt>0)?heliDown:(tilt<0)?heliUp:heliLevel;
        else img=carImg;
        int drawY=py-vh;

        g.setColor(0x1A1A1A); g.fillArc(sx+3,groundY-5,vw-6,8,0,360);

        if(img!=null){
            g.drawImage(img,sx-1,drawY,Graphics.TOP|Graphics.LEFT);
            g.drawImage(img,sx+1,drawY,Graphics.TOP|Graphics.LEFT);
            g.drawImage(img,sx,drawY-1,Graphics.TOP|Graphics.LEFT);
            g.drawImage(img,sx,drawY+1,Graphics.TOP|Graphics.LEFT);
            g.drawImage(img,sx,drawY,Graphics.TOP|Graphics.LEFT);
        } else { g.setColor(0xFF0000); g.fillRect(sx,drawY,vw,vh); }

        if(mode==MODE_FLY){
            int bladeY=drawY+2; int spin=frame%4;
            g.setColor(0x333333);
            if(spin<2) g.fillRect(sx+6,bladeY,vw-4,2);
            else g.fillArc(sx+8,bladeY-3,vw-12,6,0,360);
        }

        g.setColor(0x000000); g.fillRect(2,2,74,10);
        g.setColor(0xD32F2F); g.fillRect(3,3,php*72/100,8);
        g.setColor(0xFFFFFF);
        g.drawString(mode==MODE_FLY?"FLY":"DRIVE",W-4,2,Graphics.TOP|Graphics.RIGHT);
        String bs = bombOn?"BOMB!":(bombCd>0?("BOMB "+(bombCd/25+1)):"BOMB RDY");
        g.drawString(bs,4,14,Graphics.TOP|Graphics.LEFT);
        if(bossSpawned) g.drawString(bossOn?"BOSS!":"CLEARING",W-4,14,Graphics.TOP|Graphics.RIGHT);

        if(state==DEAD || state==WIN){
            g.setColor(0x000000); g.fillRect(W/2-70,H/2-24,140,48);
            g.setColor(0xFFFFFF);
            g.drawString(state==DEAD?"YOU DIED":"YOU WIN!",W/2,H/2-18,Graphics.TOP|Graphics.HCENTER);
            if(deadTimer>25 && ((frame>>3)&1)==0)
                g.drawString("PRESS FIRE",W/2,H/2,Graphics.TOP|Graphics.HCENTER);
        }
    }
}
