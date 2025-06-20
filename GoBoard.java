// Jason He
// 6/19/25
// GoBoard.Java
/* This program is version 1 of a simple Go Board with a few functions: 
 * 1) Create a board, 2) Save board, 3) View saved boards, 4) Run through each 
 * board (with the slider at the bottom of the screen), and 5) Change board 
 * colors. All rules have been implemented.
 * 
 * MAKE SURE YOU HAVE A SUITABLE JAVA SWING ENVIRONMENT TO RUN THIS CODE.
 * 
 * COMING IN VERSION 2 (Releasing July 31, 2025):
 * Full implementation of Ko rule
 * Ability to edit saved boards
 * Expanded settings to accomodate sounds
 * Graphical interface on homepage
 * Bug fixes
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;

public class GoBoard 
{
	// main
	public static void main(String[] args) 
    {
        SwingUtilities.invokeLater(() -> 
        {
            JFrame frame = new JFrame("Weiqi Opening Experimenter");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 200);
            frame.setLocationRelativeTo(null);

            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(3, 1, 10, 10));

            JButton newBoardBtn = new JButton("New Board");
            JButton oldBoardsBtn = new JButton("Old Boards");
            JButton settingsBtn = new JButton("Settings");

            newBoardBtn.addActionListener(e -> new GoBoard());
            oldBoardsBtn.addActionListener(e -> {
                File dir = new File("saved_boards");
                if (!dir.exists() || dir.listFiles() == null || dir.listFiles().length == 0) {
                    JOptionPane.showMessageDialog(frame, "No saved boards found.");
                    return;
                }
                File[] files = dir.listFiles((d, name) -> name.endsWith(".goboard"));
                if (files == null || files.length == 0) {
                    JOptionPane.showMessageDialog(frame, "No saved boards found.");
                    return;
                }
                String[] names = new String[files.length];
                for (int i = 0; i < files.length; i++) {
                    names[i] = files[i].getName().replace(".goboard", "");
                }
                String selected = (String) JOptionPane.showInputDialog(frame, "Select a board to load:", "Old Boards", JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
                if (selected != null) {
                    GoBoard.loadBoardFromFile(selected);
                }
            });
            settingsBtn.addActionListener(e -> GoBoard.showStaticSettingsDialog(frame));

            panel.add(newBoardBtn);
            panel.add(oldBoardsBtn);
            panel.add(settingsBtn);

            frame.add(panel);
            frame.setVisible(true);
        });
    }
    
    private final int SIZE = 19; // 19 x 19 traditional board
    private int[][] board = new int[SIZE][SIZE]; // 0: empty, 1: black, 2: white
    private boolean blackTurn = true; // black plays first
    private int lastMoveX = -1, lastMoveY = -1, lastMoveColor = 0; // need to track last moves
    private int blackCaptures = 0; // prisoners
    private int whiteCaptures = 0; // prisoners
    private JLabel blackBanner; // banner label at top
    private JLabel whiteBanner; // banner label at top
    private JLabel moveNumberLabel; // label that displays move number, constantly updated
    
    private java.util.List<BoardState> history = new ArrayList<>(); // list of board state
    private int historyIndex = 0; // to track for slider
    private GoBoardPanel boardPanel; // go board object
    private JSlider moveSlider; // slider that can view between moves
    private JButton resetBoardBtn, returnHomeBtn, settingsBtn; // necessary jbuttons for function
    private JFrame frame; // holding frame
    private boolean viewOnly = false; // for saved boards
    private static Color defaultBoardColor = new Color(222, 184, 135); // default for all new boards
    private Color boardColor = defaultBoardColor; // instance color
    
    // colors
    private static final Color[] PLEASING_COLORS = 
    {
        new Color(222, 184, 135), // light wood
        new Color(205, 133, 63),  // deeper wood
        new Color(200, 200, 200), // light gray
        new Color(180, 205, 205), // soft blue
        new Color(180, 220, 180), // soft green
        new Color(245, 245, 220)  // beige
    };
    private static final String[] COLOR_NAMES = 
    {
        "Light Wood", "Deep Wood", "Light Gray", "Soft Blue", "Soft Green", "Beige"
    };
    private static final java.util.List<GoBoard> openBoards = new ArrayList<>();

	// constructor
    public GoBoard() 
    {
        this(null, false);
        this.boardColor = defaultBoardColor;
    }

	// overloaded constructor
    public GoBoard(java.util.List<BoardState> loadedHistory) 
    {
        this(loadedHistory, true);
    }

	// overloaded constructor #2
    private GoBoard(java.util.List<BoardState> loadedHistory, boolean viewOnly) 
    {
        this.viewOnly = viewOnly;
        this.boardColor = defaultBoardColor;
        openBoards.add(this);
        frame = new JFrame(viewOnly ? "Weiqi (Go) Board (View Only)" : "Weiqi (Go) Board");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(850, 950);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        JPanel bannerPanel = new JPanel(new BorderLayout());
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        // banners
        blackBanner = new JLabel(" Black Captures: 0 ", SwingConstants.CENTER);
        blackBanner.setOpaque(true);
        blackBanner.setBackground(Color.BLACK);
        blackBanner.setForeground(Color.WHITE);
        blackBanner.setFont(new Font("Arial", Font.BOLD, 18));

        whiteBanner = new JLabel(" White Captures: 0 ", SwingConstants.CENTER);
        whiteBanner.setOpaque(true);
        whiteBanner.setBackground(Color.WHITE);
        whiteBanner.setForeground(Color.BLACK);
        whiteBanner.setFont(new Font("Arial", Font.BOLD, 18));

        moveNumberLabel = new JLabel("Move: 0 ", SwingConstants.RIGHT);
        moveNumberLabel.setFont(new Font("Arial", Font.BOLD, 18));
        moveNumberLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));

        bannerPanel.add(blackBanner, BorderLayout.WEST);
        bannerPanel.add(whiteBanner, BorderLayout.CENTER);
        bannerPanel.add(moveNumberLabel, BorderLayout.EAST);

        // board panel
        boardPanel = new GoBoardPanel();

        // move slider
        moveSlider = new JSlider(0, 0, 0);
        moveSlider.setMajorTickSpacing(1);
        moveSlider.setPaintTicks(true);
        moveSlider.setPaintLabels(true);
        moveSlider.setSnapToTicks(true);
        moveSlider.addChangeListener(evt -> 
        {
            if (moveSlider.getValue() != historyIndex) 
            {
                jumpToMove(moveSlider.getValue());
            }
        });
        bottomPanel.add(moveSlider, BorderLayout.NORTH);

        // necessary control buttons
        resetBoardBtn = new JButton("Reset Board");
        returnHomeBtn = new JButton("Return to Home");
        settingsBtn = new JButton("Settings");

        if (!viewOnly) controlPanel.add(resetBoardBtn);
        controlPanel.add(returnHomeBtn);
        controlPanel.add(settingsBtn);
        bottomPanel.add(controlPanel, BorderLayout.SOUTH);

        // add panels to main panel
        mainPanel.add(bannerPanel, BorderLayout.NORTH);
        mainPanel.add(boardPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        frame.add(mainPanel);
        frame.setVisible(true);

        // if loading, restore state, otherwise just reset for new board
        if (loadedHistory != null && !loadedHistory.isEmpty()) 
        {
            history = loadedHistory;
            BoardState last = history.get(history.size() - 1);
            for (int i = 0; i < SIZE; i++)
                System.arraycopy(last.board[i], 0, board[i], 0, SIZE);
            blackTurn = last.blackTurn;
            blackCaptures = last.blackCaptures;
            whiteCaptures = last.whiteCaptures;
            lastMoveX = last.lastMoveX;
            lastMoveY = last.lastMoveY;
            historyIndex = history.size() - 1;
        } 
        // full reset of all variables
        else 
        {
            for (int i = 0; i < SIZE; i++)
                for (int j = 0; j < SIZE; j++)
                    board[i][j] = 0;
            blackTurn = true;
            lastMoveX = -1;
            lastMoveY = -1;
            lastMoveColor = 0;
            blackCaptures = 0;
            whiteCaptures = 0;
            history = new ArrayList<>();
            historyIndex = 0;
            saveHistory();
        }
        updateSlider();
        updateMoveNumberLabel();
        updateBanners();
        moveSlider.setValue(history.size() - 1);
        boardPanel.repaint();

        // button actions
        if (!viewOnly) 
        {
			// action listener for reset
            resetBoardBtn.addActionListener(evt -> 
            {
                if (history.size() >= 11) 
                {
                    int result = JOptionPane.showConfirmDialog(boardPanel, "Save board before resetting?", "Save Board", JOptionPane.YES_NO_CANCEL_OPTION);
                    if (result == JOptionPane.YES_OPTION) 
                    {
                        String name = JOptionPane.showInputDialog(boardPanel, "Enter a name for this board:", "Board " + System.currentTimeMillis());
                        if (name != null && !name.trim().isEmpty()) 
                        {
                            saveBoardToFile(name.trim());
                        } 
                        else 
                        {
                            return; // cancel reset if no name given
                        }
                    }
                    else if (result == JOptionPane.CANCEL_OPTION) 
                    {
                        return; // cancel reset
                    }
                }
                
                for (int i = 0; i < SIZE; i++)
                    for (int j = 0; j < SIZE; j++)
                        board[i][j] = 0;
                blackTurn = true;
                lastMoveX = -1;
                lastMoveY = -1;
                lastMoveColor = 0;
                blackCaptures = 0;
                whiteCaptures = 0;
                history = new ArrayList<>();
                historyIndex = 0;
                saveHistory();
                updateBanners();
                updateSlider();
                updateMoveNumberLabel();
                moveSlider.setValue(history.size() - 1); // always go to latest move
                boardPanel.repaint();
            });
        }

		// action listener for return home, basically a copy of reset
        returnHomeBtn.addActionListener(evt -> 
        {
            if (!viewOnly) 
            {
                if (history.size() >= 11) 
                {
                    int result = JOptionPane.showConfirmDialog(boardPanel, "Save board before returning to home?", "Save Board", JOptionPane.YES_NO_CANCEL_OPTION);
                    if (result == JOptionPane.YES_OPTION) 
                    {
                        String name = JOptionPane.showInputDialog(boardPanel, "Enter a name for this board:", "Board " + System.currentTimeMillis());
                        if (name != null && !name.trim().isEmpty()) 
                        {
                            saveBoardToFile(name.trim());
                            frame.dispose();
                        } 
                        else 
                        {
                            return; // cancel return if no name given
                        }
                    } 
                    else if (result == JOptionPane.NO_OPTION) 
                    {
                        frame.dispose();
                    } // else (Cancel) do nothing
                } 
                else 
                {
                    frame.dispose();
                }
            } 
            else 
            {
                frame.dispose();
            }
        });
        
        settingsBtn.addActionListener(evt -> showSettingsDialog());

        // add window listener for save-on-close
        frame.addWindowListener(new WindowAdapter() 
        {
            // overrides
            public void windowClosing(WindowEvent evt) 
            {
                if (!viewOnly) 
                {
                    if (history.size() >= 11) 
                    {
                        int result = JOptionPane.showConfirmDialog(frame, "Save board?", "Save Board", JOptionPane.YES_NO_CANCEL_OPTION);
                        if (result == JOptionPane.YES_OPTION) 
                        {
                            String name = JOptionPane.showInputDialog(frame, "Enter a name for this board:", "Board " + System.currentTimeMillis());
                            if (name != null && !name.trim().isEmpty()) 
                            {
                                saveBoardToFile(name.trim());
                                frame.dispose();
                            }
                        } 
                        else if (result == JOptionPane.NO_OPTION) 
                        {
                            frame.dispose();
                        } // else (cancel) do nothing
                    } 
                    else 
                    {
                        frame.dispose();
                    }
                } 
                else 
                {
                    frame.dispose();
                }
                openBoards.remove(GoBoard.this);
            }
        });
    }

	// update every move
    private void updateBanners() 
    {
        blackBanner.setText(" Black Captures: " + blackCaptures + " ");
        whiteBanner.setText(" White Captures: " + whiteCaptures + " ");
    }

	// update every move
    private void updateMoveNumberLabel() 
    {
        moveNumberLabel.setText("Move: " + historyIndex);
    }

	// make sure saved
    private void saveHistory() 
    {
        int[][] boardCopy = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++)
            System.arraycopy(board[i], 0, boardCopy[i], 0, SIZE);
        if (historyIndex < history.size() - 1) 
        {
            history = history.subList(0, historyIndex + 1);
        }
        history.add(new BoardState(boardCopy, blackTurn, blackCaptures, whiteCaptures, lastMoveX, lastMoveY));
        historyIndex = history.size() - 1;
    }
	
	// when opening old boards
    private void jumpToMove(int idx) 
    {
        if (idx < 0 || idx >= history.size()) 
			return;
			
        BoardState state = history.get(idx);
        for (int i = 0; i < SIZE; i++)
            System.arraycopy(state.board[i], 0, board[i], 0, SIZE);
        blackTurn = state.blackTurn;
        blackCaptures = state.blackCaptures;
        whiteCaptures = state.whiteCaptures;
        lastMoveX = state.lastMoveX;
        lastMoveY = state.lastMoveY;
        historyIndex = idx;
        updateBanners();
        updateMoveNumberLabel();
        boardPanel.repaint();
    }

	// update the move slider
    private void updateSlider() 
    {
        moveSlider.setMaximum(history.size() - 1);
        moveSlider.setValue(historyIndex);
        moveSlider.setLabelTable(moveSlider.createStandardLabels(1));
    }

	// full settings code
    private void showSettingsDialog() 
    {
        JTabbedPane tabbedPane = new JTabbedPane();

        // tab to delete board 
        JPanel deletePanel = new JPanel(new BorderLayout());
        File dir = new File("saved_boards");
        File[] files = dir.exists() ? dir.listFiles((d, name) -> name.endsWith(".goboard")) : new File[0];
        DefaultListModel<String> model = new DefaultListModel<>();
        if (files != null) for (File f : files) model.addElement(f.getName().replace(".goboard", ""));
        JList<String> boardList = new JList<>(model);
        JScrollPane scrollPane = new JScrollPane(boardList);
        JButton deleteBtn = new JButton("Delete Selected Board");
        deletePanel.add(scrollPane, BorderLayout.CENTER);
        deletePanel.add(deleteBtn, BorderLayout.SOUTH);
        
        // button action listener
        deleteBtn.addActionListener(evt -> 
        {
            String selected = boardList.getSelectedValue();
            if (selected != null) 
            {
                int confirm = JOptionPane.showConfirmDialog(frame, "Are you sure you want to delete '" + selected + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) 
                {
                    File f = new File("saved_boards", selected + ".goboard");
                    if (f.exists() && f.delete()) 
                    {
                        JOptionPane.showMessageDialog(frame, "Board deleted.");
                        model.removeElement(selected);
                    } 
                    else 
                    {
                        JOptionPane.showMessageDialog(frame, "Failed to delete board.");
                    }
                }
            }
        });
        
        tabbedPane.addTab("Delete Board", deletePanel);

        // tab to change colors
        JPanel colorPanel = new JPanel(new GridLayout(PLEASING_COLORS.length, 1, 5, 5));
        ButtonGroup colorGroup = new ButtonGroup();
        JRadioButton[] colorButtons = new JRadioButton[PLEASING_COLORS.length];
        for (int i = 0; i < PLEASING_COLORS.length; i++) 
        {
            colorButtons[i] = new JRadioButton(COLOR_NAMES[i]);
            colorButtons[i].setBackground(PLEASING_COLORS[i]);
            colorButtons[i].setOpaque(true);
            colorGroup.add(colorButtons[i]);
            colorPanel.add(colorButtons[i]);
            if (boardColor.equals(PLEASING_COLORS[i])) colorButtons[i].setSelected(true);
        }
        tabbedPane.addTab("Change Board Color", colorPanel);

        int result = JOptionPane.showConfirmDialog(frame, tabbedPane, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) 
        {
            for (int i = 0; i < colorButtons.length; i++) 
            {
                if (colorButtons[i].isSelected()) 
                {
                    defaultBoardColor = PLEASING_COLORS[i];
                    for (GoBoard gb : openBoards) 
                    {
                        gb.boardColor = defaultBoardColor;
                        gb.boardPanel.repaint();
                    }
                    break;
                }
            }
        }
    }

	// the actual board
    class GoBoardPanel extends JPanel 
    {
        private final int MARGIN = 40; // increased margin to accommodate labels
        private final int GRID_SIZE = 32; // individual grid size
        private final int STONE_SIZE = 24; // individual stone size
        private final String[] LETTERS = {"A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"}; // skipped 'I', traditional Go coords
        private int hoverX = -1, hoverY = -1; // to store hover coordinates, have hover effect

        public GoBoardPanel() 
        {
            setPreferredSize(new Dimension(MARGIN * 2 + GRID_SIZE * (SIZE - 1), MARGIN * 2 + GRID_SIZE * (SIZE - 1)));
            
            addMouseListener(new MouseAdapter() 
            {
                // overrides
                public void mouseClicked(MouseEvent evt) 
                {
                    if (viewOnly) return;
                    if (historyIndex != history.size() - 1) 
                    {
                        moveSlider.setValue(history.size() - 1); // Always go to latest move if user clicks
                        return;
                    }
                    int x = Math.round((float)(evt.getX() - MARGIN) / GRID_SIZE);
                    int y = Math.round((float)(evt.getY() - MARGIN) / GRID_SIZE);
                    if (x >= 0 && x < SIZE && y >= 0 && y < SIZE && board[x][y] == 0) 
                    {
                        int color = blackTurn ? 1 : 2;
                        
                        // check if move is valid - remember, you cannot place in a "suicide" spot- capturing yourself
                        if (!isValidMove(x, y, color)) 
                        {
                            JOptionPane.showMessageDialog(frame, "Invalid move (suicide).", "Invalid Move", JOptionPane.ERROR_MESSAGE);
                            return; // invalid move, don't place stone
                        }
                        
                        // place the stone
                        board[x][y] = color;
                        lastMoveX = x;
                        lastMoveY = y;
                        lastMoveColor = color;
                        
                        // remove captured stones
                        int captured = removeCapturedStones(x, y, 3 - color);
                        if (color == 1) blackCaptures += captured;
                        else whiteCaptures += captured;
                        
                        updateBanners();
                        blackTurn = !blackTurn;
                        saveHistory();
                        updateSlider();
                        updateMoveNumberLabel();
                        moveSlider.setValue(history.size() - 1); // for slider, always go to latest move after a move
                        repaint();
                    }
                }

                public void mouseExited(MouseEvent evt) 
                {
                    hoverX = -1;
                    hoverY = -1;
                    repaint();
                }
            });

			// add the motion listener when hovering over a valid coordinate point
            addMouseMotionListener(new MouseMotionAdapter() 
            {
                public void mouseMoved(MouseEvent evt) 
                {
                    if (viewOnly) return;

                    int x = Math.round((float)(evt.getX() - MARGIN) / GRID_SIZE);
                    int y = Math.round((float)(evt.getY() - MARGIN) / GRID_SIZE);

                    if (x >= 0 && x < SIZE && y >= 0 && y < SIZE && board[x][y] == 0) 
                    {
                        hoverX = x;
                        hoverY = y;
                    } 
                    else 
                    {
                        hoverX = -1;
                        hoverY = -1;
                    }
                    repaint();
                }
            });
        }

		// graw the actual board and stones and coordinates
        protected void paintComponent(Graphics g) 
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // draw board background
            g2.setColor(boardColor);
            g2.fillRect(0, 0, getWidth(), getHeight());
            
            // draw coordinates
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.PLAIN, 14));
            FontMetrics fm = g2.getFontMetrics();
            
            // draw column letters (A-T, skipping I)
            for (int i = 0; i < SIZE; i++) 
            {
                String letter = LETTERS[i];
                int x = MARGIN + i * GRID_SIZE;
                // draw at top
                g2.drawString(letter, x - fm.stringWidth(letter)/2, MARGIN - 10);
                // draw at bottom
                g2.drawString(letter, x - fm.stringWidth(letter)/2, MARGIN + (SIZE-1) * GRID_SIZE + 25);
            }
            
            // draw row numbers (1-19)
            for (int i = 0; i < SIZE; i++) 
            {
                String number = String.valueOf(SIZE - i);
                // draw on left
                g2.drawString(number, MARGIN - fm.stringWidth(number) - 10, MARGIN + i * GRID_SIZE + fm.getAscent()/2);
                // draw on right
                g2.drawString(number, MARGIN + (SIZE-1) * GRID_SIZE + 15, MARGIN + i * GRID_SIZE + fm.getAscent()/2);
            }
            
            // draw grid lines
            g2.setColor(Color.BLACK);
            for (int i = 0; i < SIZE; i++) 
            {
                int x = MARGIN + i * GRID_SIZE;
                g2.drawLine(MARGIN, x, MARGIN + GRID_SIZE * (SIZE - 1), x);
                g2.drawLine(x, MARGIN, x, MARGIN + GRID_SIZE * (SIZE - 1));
            }
            
            // draw star points - nine of them
            int[] star = {3, 9, 15};
            for (int i : star) 
            {
                for (int j : star) 
                {
                    int cx = MARGIN + i * GRID_SIZE;
                    int cy = MARGIN + j * GRID_SIZE;
                    g2.fillOval(cx - 4, cy - 4, 8, 8);
                }
            }
            
            // draw stones and move number on the current stone
            int currentMoveX = -1, currentMoveY = -1;
            if (historyIndex > 0) 
            {
                BoardState state = history.get(historyIndex);
                currentMoveX = state.lastMoveX;
                currentMoveY = state.lastMoveY;
            }
            for (int i = 0; i < SIZE; i++) 
            {
                for (int j = 0; j < SIZE; j++) 
                {
                    if (board[i][j] != 0) 
                    {
                        int cx = MARGIN + i * GRID_SIZE;
                        int cy = MARGIN + j * GRID_SIZE;
                        g2.setColor(board[i][j] == 1 ? Color.BLACK : Color.WHITE);
                        g2.fillOval(cx - STONE_SIZE / 2, cy - STONE_SIZE / 2, STONE_SIZE, STONE_SIZE);
                        g2.setColor(Color.BLACK);
                        g2.drawOval(cx - STONE_SIZE / 2, cy - STONE_SIZE / 2, STONE_SIZE, STONE_SIZE);
                        // draw move number if this is the current stone
                        if (i == currentMoveX && j == currentMoveY) 
                        {
                            g2.setFont(new Font("Arial", Font.BOLD, 12));
                            g2.setColor(board[i][j] == 1 ? Color.WHITE : Color.BLACK);
                            String num = String.valueOf(historyIndex);
                            FontMetrics stoneFm = g2.getFontMetrics();
                            int tx = cx - stoneFm.stringWidth(num) / 2;
                            int ty = cy + stoneFm.getAscent() / 2 - 2;
                            g2.drawString(num, tx, ty);
                        }
                    }
                }
            }

            // draw faint stone on hover
            if (hoverX != -1 && hoverY != -1) 
            {
                int cx = MARGIN + hoverX * GRID_SIZE;
                int cy = MARGIN + hoverY * GRID_SIZE;
                Color stoneColor = blackTurn ? Color.BLACK : Color.WHITE;
                g2.setColor(new Color(stoneColor.getRed(), stoneColor.getGreen(), stoneColor.getBlue(), 128)); // 50% transparent
                g2.fillOval(cx - STONE_SIZE / 2, cy - STONE_SIZE / 2, STONE_SIZE, STONE_SIZE);
            }
        }
    }

	// when captured, remove it from the board
    private int removeCapturedStones(int x, int y, int color) 
    {
        int totalCaptured = 0;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        boolean[][] visited = new boolean[SIZE][SIZE];
        for (int[] d : dirs) 
        {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE && board[nx][ny] == color && !visited[nx][ny]) 
            {
                if (!hasLiberty(nx, ny, color, visited, new boolean[SIZE][SIZE])) 
                {
                    totalCaptured += removeGroup(nx, ny, color);
                }
            }
        }
        
        return totalCaptured;
    }

	// check if a stone still has a liberty
    private boolean hasLiberty(int x, int y, int color, boolean[][] visited, boolean[][] checked) 
    {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) 
			return false;
        if (checked[x][y]) 
			return false;
        checked[x][y] = true;
        if (board[x][y] == 0) 
			return true;
        if (board[x][y] != color) 
			return false;
        visited[x][y] = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) 
        {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE) 
            {
                if (board[nx][ny] == 0) 
					return true;
                if (board[nx][ny] == color && !visited[nx][ny]) 
                {
                    if (hasLiberty(nx, ny, color, visited, checked)) 
						return true;
                }
            }
        }
        return false;
    }

	// when capturing a group (aka a group has no more liberties)
    private int removeGroup(int x, int y, int color) 
    {
        int count = 0;
        Stack<Point> stack = new Stack<>();
        stack.push(new Point(x, y));
        board[x][y] = 0;
        count++;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!stack.isEmpty()) 
        {
            Point p = stack.pop();
            for (int[] d : dirs) 
            {
                int nx = p.x + d[0], ny = p.y + d[1];
                if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE && board[nx][ny] == color) 
                {
                    board[nx][ny] = 0;
                    stack.push(new Point(nx, ny));
                    count++;
                }
            }
        }
        
        return count;
    }

	// save the boards to a file
    private void saveBoardToFile(String name) 
    {
        try 
        {
            File dir = new File("saved_boards");
            if (!dir.exists()) dir.mkdir();
            File file = new File(dir, name + ".goboard");
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
            out.writeObject(history);
            out.close();
        } 
        catch (IOException ex) 
        {
            JOptionPane.showMessageDialog(null, "Failed to save board: " + ex.getMessage());
        }
    }

    // board state for history
    private static class BoardState implements Serializable 
    {
        int[][] board;
        boolean blackTurn;
        int blackCaptures, whiteCaptures;
        int lastMoveX, lastMoveY;
        BoardState(int[][] b, boolean turn, int bc, int wc, int lmx, int lmy) 
        {
            board = new int[19][19];
            for (int i = 0; i < 19; i++)
                System.arraycopy(b[i], 0, board[i], 0, 19);
            blackTurn = turn;
            blackCaptures = bc;
            whiteCaptures = wc;
            lastMoveX = lmx;
            lastMoveY = lmy;
        }
    }

    @SuppressWarnings("unchecked") // keeps getting warnings in terminal
    
    // load the old boards
    public static void loadBoardFromFile(String name) 
    {
        try 
        {
            File file = new File("saved_boards", name + ".goboard");
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
            java.util.List<BoardState> loadedHistory = (java.util.List<BoardState>) in.readObject();
            in.close();
            if (loadedHistory != null && !loadedHistory.isEmpty()) 
            {
                new GoBoard(loadedHistory, true);
            }
        } 
        catch (Exception ex) 
        {
            JOptionPane.showMessageDialog(null, "Failed to load board: " + ex.getMessage());
        }
    }

	// same settings as the one before, just different places
    public static void showStaticSettingsDialog(Window parent) 
    {
        JTabbedPane tabbedPane = new JTabbedPane();

        // delete board tab
        JPanel deletePanel = new JPanel(new BorderLayout());
        File dir = new File("saved_boards");
        File[] files = dir.exists() ? dir.listFiles((d, name) -> name.endsWith(".goboard")) : new File[0];
        DefaultListModel<String> model = new DefaultListModel<>();
        if (files != null) for (File f : files) model.addElement(f.getName().replace(".goboard", ""));
        JList<String> boardList = new JList<>(model);
        JScrollPane scrollPane = new JScrollPane(boardList);
        JButton deleteBtn = new JButton("Delete Selected Board");
        deletePanel.add(scrollPane, BorderLayout.CENTER);
        deletePanel.add(deleteBtn, BorderLayout.SOUTH);
        
        // add the action listener to delete the board
        deleteBtn.addActionListener(evt -> 
        {
            String selected = boardList.getSelectedValue();
            if (selected != null) 
            {
                int confirm = JOptionPane.showConfirmDialog(parent, "Are you sure you want to delete '" + selected + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) 
                {
                    File f = new File("saved_boards", selected + ".goboard");
                    if (f.exists() && f.delete()) 
                    {
                        JOptionPane.showMessageDialog(parent, "Board deleted.");
                        model.removeElement(selected);
                    } 
                    else 
                    {
                        JOptionPane.showMessageDialog(parent, "Failed to delete board.");
                    }
                }
            }
        });
        
        tabbedPane.addTab("Delete Board", deletePanel);

        // change board color tab
        JPanel colorPanel = new JPanel(new GridLayout(PLEASING_COLORS.length, 1, 5, 5));
        ButtonGroup colorGroup = new ButtonGroup();
        JRadioButton[] colorButtons = new JRadioButton[PLEASING_COLORS.length];
        for (int i = 0; i < PLEASING_COLORS.length; i++) 
        {
            colorButtons[i] = new JRadioButton(COLOR_NAMES[i]);
            colorButtons[i].setBackground(PLEASING_COLORS[i]);
            colorButtons[i].setOpaque(true);
            colorGroup.add(colorButtons[i]);
            colorPanel.add(colorButtons[i]);
            if (defaultBoardColor.equals(PLEASING_COLORS[i])) colorButtons[i].setSelected(true);
        }
        
        tabbedPane.addTab("Change Board Color", colorPanel);

        int result = JOptionPane.showConfirmDialog(parent, tabbedPane, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) 
        {
            for (int i = 0; i < colorButtons.length; i++) 
            {
                if (colorButtons[i].isSelected()) 
                {
                    defaultBoardColor = PLEASING_COLORS[i];
                    for (GoBoard gb : openBoards) 
                    {
                        gb.boardColor = defaultBoardColor;
                        gb.boardPanel.repaint();
                    }
                    
                    break;
                }
            }
        }
    }

    // check if a move is valid (suicide rule)
    private boolean isValidMove(int x, int y, int color) 
    {
        // create a deep copy of the board to check for suicide without side effects.
        int[][] boardCopy = new int[SIZE][SIZE];
        for(int i = 0; i < SIZE; i++) 
        {
            System.arraycopy(this.board[i], 0, boardCopy[i], 0, SIZE);
        }

        // temporarily swap `this.board` with our copy to use existing helper methods.
        int[][] realBoard = this.board;
        this.board = boardCopy;

        // now, perform checks on this.board, (which is the copy)
        board[x][y] = color; // place stone on copy

        boolean suicide = false;
        // we check if the stone has any liberties left with our hasliberty method
        if (!hasLiberty(x, y, color, new boolean[SIZE][SIZE], new boolean[SIZE][SIZE])) 
        {
            // If not, check if placing it results in capturing opponent stones.
            int capturedStones = removeCapturedStones(x, y, 3 - color);
            if (capturedStones == 0) 
            {
                // If no stones are captured, it's a suicide move.
                suicide = true;
            }
        }

        /// IMPORTANT: restore the original board.
        this.board = realBoard;

        return !suicide;
    }
} 
