package kr.co.mz.tutorial.jdbc.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.Part;
import kr.co.mz.tutorial.jdbc.db.dao.BoardDao;
import kr.co.mz.tutorial.jdbc.db.dao.BoardFileDao;
import kr.co.mz.tutorial.jdbc.db.dao.CommentDao;
import kr.co.mz.tutorial.jdbc.db.dao.LikesDao;
import kr.co.mz.tutorial.jdbc.db.model.Board;
import kr.co.mz.tutorial.jdbc.db.model.BoardFile;
import kr.co.mz.tutorial.jdbc.db.model.Customer;
import kr.co.mz.tutorial.jdbc.exception.DatabaseAccessException;
import kr.co.mz.tutorial.jdbc.exception.FileLimitException;
import kr.co.mz.tutorial.jdbc.exception.PartsIOException;
import kr.co.mz.tutorial.jdbc.file.FileService;

public class BoardService {

    private final Connection connection;

    public BoardService(Connection connection) {
        this.connection = connection;
    }

    public List<Board> getByCategory(String category) {
        if (category.equals("전체")) {
            return new BoardDao(connection).findAll();
        }
        return new BoardDao(connection).findAny(category);
    }

    public int write(Board board, Customer customer) {
        var boardSeq = 0;
        try {
            connection.setAutoCommit(false);
            board.setCustomerSeq(customer.getSeq());
            boardSeq = new BoardDao(connection).insertOne(board);
            for (BoardFile boardFile : board.getBoardFileSet()) {
                boardFile.setBoardSeq(boardSeq);
                new BoardFileDao(connection).insertOne(boardFile);
            }
            connection.commit();
        } catch (SQLException sqle) {
            try {
                connection.rollback();
            } catch (SQLException sqle2) {
                throw new DatabaseAccessException(sqle2);
            }
        }
        return boardSeq;
    }

    public Optional<Board> view(int boardSeq) {
        return new BoardDao(connection).findOne(boardSeq);
    }

    public int modify(Board board, Collection<Part> parts) {
        try {
            connection.setAutoCommit(false);
            var fileService = new FileService();
            var writeYN = 0;
            var boardFileSet = fileService.upload(parts, null, writeYN);
            if (boardFileSet == null) {
                throw new FileLimitException("history.back()");
            }
            board.setBoardFileSet(boardFileSet);
            new BoardDao(connection).updateOne(board);
            BoardFileDao boardFileDao = new BoardFileDao(connection);

            List<BoardFile> deleteFileList = boardFileDao.findAllByBoardSeq(board.getSeq());
            boardFileDao.deleteAllFromBoardSeq(board.getSeq());

            for (BoardFile boardFile : board.getBoardFileSet()) {
                boardFile.setBoardSeq(board.getSeq());
                boardFileDao.insertOne(boardFile);
            }
            List<BoardFile> writeFileList = boardFileDao.findAllByBoardSeq(board.getSeq());

            writeYN = 1;
            for (BoardFile boardFile : deleteFileList) {
                fileService.delete(boardFile.getFilePath());
            }
            fileService.upload(parts, writeFileList, writeYN);
            connection.commit();
        } catch (SQLException sqle) {
            try {
                connection.rollback();
            } catch (SQLException sqle2) {
                throw new DatabaseAccessException(sqle2);
            }
            throw new DatabaseAccessException(sqle);
        } catch (IOException ioe) {
            throw new PartsIOException(ioe);
        }
        return 1;
    }

    public int delete(int boardSeq) {
        try {
            connection.setAutoCommit(false);
            var boardFileDao = new BoardFileDao(connection);
            var filePathList = boardFileDao.findAllFromBoardSeq(boardSeq);
            boardFileDao.deleteAllFromBoardSeq(boardSeq);
            new CommentDao(connection).deleteAllByBoardSeq(boardSeq);
            int result = new BoardDao(connection).deleteOne(boardSeq);
            for (String filePath : filePathList) {
                boolean flag = new FileService().delete(filePath);
                if (flag) {
                    System.out.println("Success Delete File on Server Storage : " + filePath);
                }
            }
            connection.commit();
            return result;
        } catch (SQLException sqle) {
            try {
                connection.rollback();
            } catch (SQLException sqle2) {
                throw new DatabaseAccessException(sqle2);
            }
            throw new DatabaseAccessException(sqle);
        }
    }

    public int likesCount(Customer customer, int boardSeq) {
        try {
            connection.setAutoCommit(false);
            var likesDao = new LikesDao(connection);
            int likesPrimaryKey = likesDao.findOne(boardSeq, customer.getSeq());
            new BoardDao(connection).updateOneOfLikesCount(boardSeq, likesPrimaryKey);
            int result;
            if (likesPrimaryKey != 0) {
                result = likesDao.deleteOne(likesPrimaryKey);
            } else {
                result = likesDao.insertOne(boardSeq, customer.getSeq());
            }
            connection.commit();
            return result;
        } catch (SQLException sqle) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                throw new DatabaseAccessException(sqle);
            }
            throw new DatabaseAccessException(sqle);
        }
    }
}
