import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow tela;

    private SwingWorker executavel;


//arrays lists e variaveis responsaveis pelo armazenamento das musicas
    private ArrayList <Song> musicas = new ArrayList<Song>();
    private ArrayList<String[]> musica_temp = new ArrayList<String[]>();
    private String[][] array;

//variaveis que vao ser usadas em diversas funções
    private Song musica_atual;
    private Song remocao_musica;
    private int index;

    private boolean botao_pause = true;
    private boolean botao_pause_press = true;
    private boolean stop_press = false;


//funcao para dar stop na musica
private void stop(){
    botao_pause_press = false;
    tela.setEnabledStopButton(false);
    tela.resetMiniPlayer();
}

    private int currentFrame = 0;

    private final ActionListener buttonListenerPlayNow = e->{

        currentFrame = 0;
        index = tela.GetIndex();
        musica_atual = musicas.get(index);
        botao_pause_press = true;

        executavel = new SwingWorker() {
            @Override
            public Object doInBackground() throws Exception {
                tela.setPlayingSongInfo(musica_atual.getTitle(), musica_atual.getAlbum(), musica_atual.getArtist());

            if(bitstream != null){
                try{
                    bitstream.close();
                } catch (BitstreamException ex) {
                    throw new RuntimeException(ex);
                }

                device.close();
            }

            try {
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(musica_atual.getBufferedInputStream());
            } catch (JavaLayerException | FileNotFoundException ex){
                throw new RuntimeException(ex);
            }

            while(true){
                if(botao_pause_press){
                    try{
                        tela.setTime((int) (currentFrame * (int) musica_atual.getMsPerFrame()), (int) musica_atual.getMsLength());
                        tela.setPlayPauseButtonIcon(1);
                        tela.setEnabledPlayPauseButton(true);
                        tela.setEnabledStopButton(true);
                        botao_pause_press = true;
                        stop_press = true;

                        playNextFrame();
                    } catch (JavaLayerException ex){
                        throw new RuntimeException();
                    }
                }
            }
            }
        };
        executavel.execute();
    };
    private final ActionListener buttonListenerRemove = e -> {
        index = tela.GetIndex();
        remocao_musica = musicas.get(index);

        musica_temp.remove(index);
        array = musica_temp.toArray(new String[this.musica_temp.size()][7]);
        tela.setQueueList(array);

        musicas.remove(index);

        if(currentFrame != 0 && musica_atual == remocao_musica){
            stop();
        }
    };
    private final ActionListener buttonListenerAddSong = e-> {
       try {
           //recebendo input de música e adicionando na fila de reprodução
           Song nova_musica;
           nova_musica = this.tela.openFileChooser();
           musicas.add(nova_musica);
           //setando a nova música escolhida para aparecer no display
           musica_temp.add(nova_musica.getDisplayInfo());
           array = musica_temp.toArray(new String[this.musica_temp.size()][7]);
           tela.setQueueList(array);
       }catch (IOException | InvalidDataException | BitstreamException | UnsupportedTagException ex) {
            throw new RuntimeException(ex);}
    };
    private final ActionListener buttonListenerPlayPause = e -> {
        //primeiro caso aonde a música está em execução
        if(botao_pause == true){
            botao_pause_press = false;
            botao_pause = false;
            tela.setPlayPauseButtonIcon(0);
        }
        //caso aonde a musica é pausada
        else{
            botao_pause_press = true;
            botao_pause = true;
            tela.setPlayPauseButtonIcon(1);
        }

    };
    private final ActionListener buttonListenerStop = e -> {
        if(stop_press){
            stop();
        }
    };
    private final ActionListener buttonListenerNext = e -> {};
    private final ActionListener buttonListenerPrevious = e -> {};
    private final ActionListener buttonListenerShuffle = e -> {};
    private final ActionListener buttonListenerLoop = e -> {};
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> tela = new PlayerWindow(
                ("reprodutor"),
                array,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO: Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
            currentFrame ++;
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO: Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO: Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>
}
