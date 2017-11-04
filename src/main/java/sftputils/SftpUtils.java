package sftputils;

import com.jcraft.jsch.*;

public class SftpUtils {

    /**
     * Create dir recursively, similar to shell command mkdir -p
     *
     * @param channelSftp
     * @param path        absolute or relative path
     * @throws SftpException
     */
    public static void mkdirp(ChannelSftp channelSftp, String path) throws SftpException {
        if (isBlank(path)) {
            throw new IllegalArgumentException("path cannot be blank");
        }

        String pwd = channelSftp.pwd();

        String[] parts = path.split("/");
        if (isBlank(parts[0])) {
            parts[0] = "/";
        }

        for (String part : parts) {
            if (notExists(channelSftp, part)) {
                mkdir(channelSftp, part);
            }
            cd(channelSftp, part);
        }
        // return to original path before recursively create the new path
        cd(channelSftp, pwd);
    }

    public static <T> void execute(Session session, final Action0 block) {
        execute(session, new Action1<Void>() {
            public Void execute(ChannelSftp channelSftp) throws SftpException {
                block.execute(channelSftp);
                return null;
            }
        });
    }

    public static <T> T execute(Session session, Action1<T> block) {
        ChannelSftp sftpChannel = null;
        try {
            if (!session.isConnected()) {
                session.connect();
            }

            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftpChannel = (ChannelSftp) channel;

            return block.execute(sftpChannel);

        } catch (JSchException ex) {
            throw new RuntimeException(ex);
        } catch (SftpException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (sftpChannel != null) {
                sftpChannel.disconnect();
            }
        }
    }

    public interface Action0 {
        public void execute(ChannelSftp channelSftp) throws SftpException;
    }

    public interface Action1<T> {
        public T execute(ChannelSftp channelSftp) throws SftpException;
    }

    private static boolean notExists(ChannelSftp channelSftp, String part) throws SftpException {
        try {
            SftpATTRS attrs = channelSftp.lstat(part);
            if (attrs != null && !attrs.isDir()) {
                throw new SftpException(ChannelSftp.SSH_FX_FAILURE, "object exists with the same name");
            }
            return false;
        } catch (SftpException ex) {
            if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return true;
            }
            error("notExists", channelSftp, part);
            throw ex;
        }
    }

    private static void mkdir(ChannelSftp channelSftp, String part) throws SftpException {
        try {
            channelSftp.mkdir(part);
        } catch (SftpException ex) {
            error("mkdir", channelSftp, part);
            throw ex;
        }
    }

    private static void cd(ChannelSftp channelSftp, String part) throws SftpException {
        try {
            channelSftp.cd(part);
        } catch (SftpException ex) {
            error("cd", channelSftp, part);
            throw ex;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void error(String ops, ChannelSftp channelSftp, String path) {
        try {
            System.err.println("Operation: " + ops + ", pwd:" + channelSftp.pwd() + ", path: " + path);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
    }
}
