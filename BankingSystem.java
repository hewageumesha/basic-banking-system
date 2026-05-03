import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.*;

//  CUSTOM EXCEPTIONS

class InsufficientFundsException extends Exception {
    private final double available;
    public InsufficientFundsException(double available) {
        super(String.format("Insufficient funds. Available: Rs.%.2f", available));
        this.available = available;
    }
    public double getAvailable() { return available; }
}

class InvalidAmountException extends Exception {
    public InvalidAmountException(String msg) { super(msg); }
}

class SameAccountTransferException extends Exception {
    public SameAccountTransferException() { super("Cannot transfer to the same account."); }
}

class LoanNotFoundException extends Exception {
    public LoanNotFoundException(String id) { super("Loan not found: " + id); }
}

//  TRANSACTION

class Transaction {
    public enum Type { DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN, INTEREST, LOAN_DISBURSEMENT, LOAN_PAYMENT }

    private static int counter = 1;
    private final String txId;
    private final Type type;
    private final double amount;
    private final String description;
    private final Date date;
    private final double balanceAfter;

    public Transaction(Type type, double amount, String description, double balanceAfter) {
        this.txId        = "TX" + String.format("%04d", counter++);
        this.type        = type;
        this.amount      = amount;
        this.description = description;
        this.date        = new Date();
        this.balanceAfter = balanceAfter;
    }

    public String getTxId()        { return txId; }
    public Type   getType()        { return type; }
    public double getAmount()      { return amount; }
    public String getDescription() { return description; }
    public Date   getDate()        { return date; }
    public double getBalanceAfter(){ return balanceAfter; }

    public boolean isCredit() {
        return type == Type.DEPOSIT || type == Type.TRANSFER_IN
                || type == Type.INTEREST || type == Type.LOAN_DISBURSEMENT;
    }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
        return String.format("[%s] %s | %s%s%.2f | Bal: Rs.%.2f | %s",
                txId, sdf.format(date), isCredit() ? "+" : "-",
                "Rs.", amount, balanceAfter, description);
    }
}

//  LOAN

class Loan {
    public enum LoanType { PERSONAL, AUTO, MORTGAGE }

    private static int counter = 1;
    private final String loanId;
    private final LoanType loanType;
    private final double principal;
    private final double annualRate;
    private final int termMonths;
    private final double monthlyPayment;
    private double remainingBalance;
    private int paymentsMade;
    private final Date startDate;
    private final String linkedAccountId;

    public Loan(LoanType loanType, double principal, int termMonths, String linkedAccountId) {
        this.loanId           = "LN" + String.format("%03d", counter++);
        this.loanType         = loanType;
        this.principal        = principal;
        this.termMonths       = termMonths;
        this.linkedAccountId  = linkedAccountId;
        this.remainingBalance = principal;
        this.paymentsMade     = 0;
        this.startDate        = new Date();

        this.annualRate = switch (loanType) {
            case PERSONAL -> 0.08;
            case AUTO     -> 0.055;
            case MORTGAGE -> 0.068;
        };

        double r = annualRate / 12.0;
        this.monthlyPayment = Math.round(principal * r / (1 - Math.pow(1 + r, -termMonths)) * 100.0) / 100.0;
    }

    public void makePayment() {
        double interest = remainingBalance * (annualRate / 12.0);
        double principal = monthlyPayment - interest;
        remainingBalance = Math.max(0, remainingBalance - principal);
        paymentsMade++;
    }

    public boolean isPaidOff()       { return paymentsMade >= termMonths || remainingBalance <= 0.01; }
    public String  getLoanId()        { return loanId; }
    public LoanType getLoanType()     { return loanType; }
    public double  getPrincipal()     { return principal; }
    public double  getAnnualRate()    { return annualRate; }
    public int     getTermMonths()    { return termMonths; }
    public double  getMonthlyPayment(){ return monthlyPayment; }
    public double  getRemainingBalance(){ return remainingBalance; }
    public int     getPaymentsMade()  { return paymentsMade; }
    public Date    getStartDate()     { return startDate; }
    public String  getLinkedAccountId(){ return linkedAccountId; }
    public int     getProgressPct()   { return (int)((paymentsMade / (double) termMonths) * 100); }
}

//  BANK ACCOUNT  (Base class)

abstract class BankAccount {
    private static int counter = 1;
    protected final String accountId;
    protected final String accountType;
    protected String nickname;
    protected double balance;
    protected final List<Transaction> transactions;
    protected final Date openedDate;

    public BankAccount(String accountType, String nickname) {
        this.accountId    = accountType.toUpperCase().substring(0, 3) + String.format("%03d", counter++);
        this.accountType  = accountType;
        this.nickname     = nickname;
        this.balance      = 0.0;
        this.transactions = new ArrayList<>();
        this.openedDate   = new Date();
    }

    public void deposit(double amount, String description)
            throws InvalidAmountException {
        if (amount <= 0) throw new InvalidAmountException("Deposit amount must be positive.");
        balance += amount;
        transactions.add(0, new Transaction(Transaction.Type.DEPOSIT, amount, description, balance));
    }

    public void withdraw(double amount, String description)
            throws InvalidAmountException, InsufficientFundsException {
        if (amount <= 0) throw new InvalidAmountException("Withdrawal amount must be positive.");
        if (amount > balance) throw new InsufficientFundsException(balance);
        balance -= amount;
        transactions.add(0, new Transaction(Transaction.Type.WITHDRAWAL, amount, description, balance));
    }

    public void receiveTransfer(double amount, String fromName) throws InvalidAmountException {
        if (amount <= 0) throw new InvalidAmountException("Transfer amount must be positive.");
        balance += amount;
        transactions.add(0, new Transaction(Transaction.Type.TRANSFER_IN, amount,
                "Transfer from " + fromName, balance));
    }

    public void sendTransfer(double amount, String toName)
            throws InvalidAmountException, InsufficientFundsException {
        if (amount <= 0) throw new InvalidAmountException("Transfer amount must be positive.");
        if (amount > balance) throw new InsufficientFundsException(balance);
        balance -= amount;
        transactions.add(0, new Transaction(Transaction.Type.TRANSFER_OUT, amount,
                "Transfer to " + toName, balance));
    }

    // Subclasses may override (e.g., SavingsAccount applies interest)
    public abstract String getTypeLabel();
    public abstract Color  getThemeColor();

    public String getAccountId()          { return accountId; }
    public String getAccountType()        { return accountType; }
    public String getNickname()           { return nickname; }
    public void   setNickname(String n)   { this.nickname = n; }
    public double getBalance()            { return balance; }
    public List<Transaction> getTransactions() { return transactions; }
    public Date   getOpenedDate()         { return openedDate; }

    @Override
    public String toString() {
        return String.format("%s (%s) - Rs.%.2f", nickname, accountId, balance);
    }
}

//  ACCOUNT SUBTYPES

class CheckingAccount extends BankAccount {
    public CheckingAccount(String nickname) { super("Checking", nickname); }

    @Override public String getTypeLabel()  { return "Checking Account"; }
    @Override public Color  getThemeColor() { return new Color(30, 144, 255); }
}

class SavingsAccount extends BankAccount {
    public static final double ANNUAL_RATE   = 0.035;
    public static final double MONTHLY_RATE  = ANNUAL_RATE / 12.0;

    public SavingsAccount(String nickname) { super("Savings", nickname); }

    public double applyMonthlyInterest() throws InvalidAmountException {
        double interest = Math.round(balance * MONTHLY_RATE * 100.0) / 100.0;
        if (interest > 0) {
            balance += interest;
            transactions.add(0, new Transaction(Transaction.Type.INTEREST, interest,
                    String.format("Monthly Interest (%.1f%% APY)", ANNUAL_RATE * 100), balance));
        }
        return interest;
    }

    @Override public String getTypeLabel()  { return "Savings Account (3.5% APY)"; }
    @Override public Color  getThemeColor() { return new Color(0, 180, 140); }
}

class InvestmentAccount extends BankAccount {
    public InvestmentAccount(String nickname) { super("Investment", nickname); }

    @Override public String getTypeLabel()  { return "Investment Account"; }
    @Override public Color  getThemeColor() { return new Color(220, 100, 50); }
}

//  BANK  (Central data store)

class Bank {
    private final String bankName;
    private final List<BankAccount> accounts;
    private final List<Loan>        loans;
    private String customerName;

    public Bank(String bankName, String customerName) {
        this.bankName     = bankName;
        this.customerName = customerName;
        this.accounts     = new ArrayList<>();
        this.loans        = new ArrayList<>();

        // Seed default accounts
        CheckingAccount  chk = new CheckingAccount("Primary Checking");
        SavingsAccount   sav = new SavingsAccount("Emergency Fund");
        InvestmentAccount inv = new InvestmentAccount("Growth Portfolio");
        try {
            chk.deposit(3240.50, "Opening Balance");
            sav.deposit(12800.00, "Opening Balance");
            inv.deposit(8500.00, "Opening Balance");
        } catch (InvalidAmountException ignored) {}
        accounts.add(chk);
        accounts.add(sav);
        accounts.add(inv);
    }

    public void addAccount(BankAccount acc)  { accounts.add(acc); }
    public List<BankAccount> getAccounts()   { return accounts; }
    public List<Loan>        getLoans()      { return loans; }
    public String            getBankName()   { return bankName; }
    public String            getCustomerName(){ return customerName; }
    public void              setCustomerName(String n){ customerName = n; }

    public BankAccount findAccount(String id) throws NoSuchElementException {
        return accounts.stream().filter(a -> a.getAccountId().equals(id))
                .findFirst().orElseThrow(() -> new NoSuchElementException("Account not found: " + id));
    }

    public void transfer(String fromId, String toId, double amount, String memo)
            throws InvalidAmountException, InsufficientFundsException,
            SameAccountTransferException, NoSuchElementException {
        if (fromId.equals(toId)) throw new SameAccountTransferException();
        BankAccount from = findAccount(fromId);
        BankAccount to   = findAccount(toId);
        from.sendTransfer(amount, to.getNickname() + (memo.isEmpty() ? "" : " - " + memo));
        to.receiveTransfer(amount, from.getNickname() + (memo.isEmpty() ? "" : " - " + memo));
    }

    public Loan applyLoan(Loan.LoanType type, double amount, int termMonths, String accountId)
            throws InvalidAmountException, NoSuchElementException {
        if (amount <= 0)     throw new InvalidAmountException("Loan amount must be positive.");
        if (termMonths <= 0) throw new InvalidAmountException("Term must be at least 1 month.");
        BankAccount acc = findAccount(accountId);
        Loan loan = new Loan(type, amount, termMonths, accountId);
        loans.add(loan);
        acc.deposit(amount, loan.getLoanType().name() + " Loan Disbursement");
        return loan;
    }

    public void makeLoanPayment(String loanId)
            throws LoanNotFoundException, InsufficientFundsException,
            InvalidAmountException, NoSuchElementException {
        Loan loan = loans.stream().filter(l -> l.getLoanId().equals(loanId))
                .findFirst().orElseThrow(() -> new LoanNotFoundException(loanId));
        if (loan.isPaidOff()) throw new InvalidAmountException("This loan is already paid off.");
        BankAccount acc = findAccount(loan.getLinkedAccountId());
        if (acc.getBalance() < loan.getMonthlyPayment())
            throw new InsufficientFundsException(acc.getBalance());
        acc.withdraw(loan.getMonthlyPayment(), loan.getLoanType().name() + " Loan Payment");
        loan.makePayment();
    }

    public double applyAllInterest() {
        double total = 0;
        for (BankAccount acc : accounts) {
            if (acc instanceof SavingsAccount sav) {
                try { total += sav.applyMonthlyInterest(); }
                catch (InvalidAmountException ignored) {}
            }
        }
        return total;
    }

    public double getTotalAssets() {
        return accounts.stream().mapToDouble(BankAccount::getBalance).sum();
    }

    public List<Transaction> getAllTransactions() {
        return accounts.stream()
                .flatMap(a -> a.getTransactions().stream())
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }
}

//  UI HELPERS

class UITheme {
    static final Color BG        = new Color(26,  35,  50);
    static final Color BG2       = new Color(36,  48,  68);
    static final Color BG3       = new Color(45,  61,  86);
    static final Color ACCENT    = new Color(0,  200, 160);
    static final Color TEXT      = new Color(232, 237, 245);
    static final Color TEXT2     = new Color(143, 163, 190);
    static final Color TEXT3     = new Color(74,  96, 128);
    static final Color RED       = new Color(255,  71,  87);
    static final Color GREEN     = new Color(46,  213, 115);
    static final Color AMBER     = new Color(255, 209, 102);
    static final Font  MONO      = new Font("Monospaced", Font.PLAIN, 13);
    static final Font  MONO_BOLD = new Font("Monospaced", Font.BOLD, 14);
    static final Font  TITLE     = new Font("Monospaced", Font.BOLD, 22);
    static final Font  SMALL     = new Font("Monospaced", Font.PLAIN, 11);
}

class StyledPanel extends JPanel {
    StyledPanel() { setBackground(UITheme.BG2); setBorder(new EmptyBorder(16,16,16,16)); }
}

class Label extends JLabel {
    Label(String text, Font font, Color color) { super(text); setFont(font); setForeground(color); }
    Label(String text) { this(text, UITheme.MONO, UITheme.TEXT); }
}

class StyledButton extends JButton {
    StyledButton(String text, Color bg, Color fg) {
        super(text);
        setFont(UITheme.MONO_BOLD);
        setBackground(bg);
        setForeground(fg);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(8, 18, 8, 18));
        addMouseListener(new MouseAdapter() {
            Color orig = bg;
            public void mouseEntered(MouseEvent e){ setBackground(orig.brighter()); }
            public void mouseExited (MouseEvent e){ setBackground(orig); }
        });
    }
}

class StyledField extends JTextField {
    StyledField(int cols) {
        super(cols);
        setFont(UITheme.MONO);
        setBackground(UITheme.BG3);
        setForeground(UITheme.TEXT);
        setCaretColor(UITheme.ACCENT);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.TEXT3),
                new EmptyBorder(6, 8, 6, 8)));
    }
}

class StyledCombo extends JComboBox<String> {
    StyledCombo() {
        setFont(UITheme.MONO);
        setBackground(UITheme.BG3);
        setForeground(UITheme.TEXT);
        setBorder(BorderFactory.createLineBorder(UITheme.TEXT3));
    }
}

//  ACCOUNT CARD PANEL

class AccountCard extends JPanel {
    AccountCard(BankAccount acc) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UITheme.BG2);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BG3, 1),
                new EmptyBorder(14, 16, 14, 16)));

        JPanel topBar = new JPanel(); topBar.setPreferredSize(new Dimension(0,3));
        topBar.setBackground(acc.getThemeColor()); topBar.setMaximumSize(new Dimension(10000,3));
        add(topBar);
        add(Box.createVerticalStrut(10));

        JLabel type = new JLabel(acc.getTypeLabel().toUpperCase());
        type.setFont(UITheme.SMALL); type.setForeground(UITheme.TEXT2);
        add(type);

        JLabel id = new JLabel(acc.getAccountId());
        id.setFont(UITheme.SMALL); id.setForeground(UITheme.TEXT3);
        add(id);
        add(Box.createVerticalStrut(8));

        JLabel bal = new JLabel(String.format("Rs.%,.2f", acc.getBalance()));
        bal.setFont(new Font("Monospaced", Font.BOLD, 20));
        bal.setForeground(UITheme.TEXT);
        add(bal);

        JLabel nick = new JLabel(acc.getNickname());
        nick.setFont(UITheme.SMALL); nick.setForeground(acc.getThemeColor());
        add(nick);
    }
}

//  TRANSACTION TABLE MODEL

class TxTableModel extends AbstractTableModel {
    private final String[] cols = {"TX ID","Date","Description","Type","Amount","Balance"};
    private List<Transaction> data = new ArrayList<>();

    public void setData(List<Transaction> data) { this.data = data; fireTableDataChanged(); }

    @Override public int getRowCount()    { return data.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c) { return cols[c]; }

    @Override
    public Object getValueAt(int r, int c) {
        Transaction t = data.get(r);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm");
        return switch (c) {
            case 0 -> t.getTxId();
            case 1 -> sdf.format(t.getDate());
            case 2 -> t.getDescription();
            case 3 -> t.getType().name().replace("_", " ");
            case 4 -> (t.isCredit() ? "+" : "-") + String.format("Rs.%,.2f", t.getAmount());
            case 5 -> String.format("Rs.%,.2f", t.getBalanceAfter());
            default -> "";
        };
    }
}

//  MAIN APPLICATION WINDOW

public class BankingSystem extends JFrame {

    private final Bank bank;
    private JPanel     mainContent;
    private CardLayout cardLayout;

    // Nav buttons
    private final Map<String, JButton> navBtns = new LinkedHashMap<>();

    public BankingSystem() {
        bank = new Bank("NovaBank", "Alex Johnson");

        setTitle("NovaBank — Complete Banking System");
        setSize(1100, 720);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UITheme.BG);
        setLayout(new BorderLayout());

        add(buildTopBar(),  BorderLayout.NORTH);
        add(buildSidebar(), BorderLayout.WEST);

        cardLayout  = new CardLayout();
        mainContent = new JPanel(cardLayout);
        mainContent.setBackground(UITheme.BG);

        mainContent.add(buildDashboard(),    "dashboard");
        mainContent.add(buildTransactions(), "transactions");
        mainContent.add(buildTransfer(),     "transfer");
        mainContent.add(buildLoans(),        "loans");
        mainContent.add(buildSettings(),     "settings");

        add(mainContent, BorderLayout.CENTER);
        showPanel("dashboard");
    }

    // ---- TOP BAR ----
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UITheme.BG2);
        bar.setBorder(new EmptyBorder(10,20,10,20));

        JLabel logo = new JLabel("NOVA");
        logo.setFont(UITheme.TITLE); logo.setForeground(UITheme.ACCENT);
        JLabel logoSub = new JLabel("BANK  ");
        logoSub.setFont(UITheme.TITLE); logoSub.setForeground(UITheme.TEXT);
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
        logoPanel.setOpaque(false); logoPanel.add(logo); logoPanel.add(logoSub);

        JLabel user = new JLabel(bank.getCustomerName() + "  |  Complete Banking System  ");
        user.setFont(UITheme.SMALL); user.setForeground(UITheme.TEXT2);

        bar.add(logoPanel, BorderLayout.WEST);
        bar.add(user,      BorderLayout.EAST);
        return bar;
    }

    // ---- SIDEBAR ----
    private JPanel buildSidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(UITheme.BG2);
        side.setBorder(new EmptyBorder(20,10,20,10));
        side.setPreferredSize(new Dimension(160, 0));

        String[][] items = {{"dashboard","Dashboard"},{"transactions","Transactions"},
                {"transfer","Transfer"},{"loans","Loans"},{"settings","Settings"}};
        for (String[] item : items) {
            JButton btn = new JButton(item[1]);
            btn.setFont(UITheme.MONO_BOLD);
            btn.setMaximumSize(new Dimension(140,38));
            btn.setAlignmentX(LEFT_ALIGNMENT);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String key = item[0];
            btn.addActionListener(e -> showPanel(key));
            navBtns.put(key, btn);
            side.add(btn);
            side.add(Box.createVerticalStrut(4));
        }
        return side;
    }

    private void showPanel(String name) {
        cardLayout.show(mainContent, name);
        navBtns.forEach((k, b) -> {
            boolean active = k.equals(name);
            b.setBackground(active ? new Color(0,212,170,60) : UITheme.BG2);
            b.setForeground(active ? UITheme.ACCENT : UITheme.TEXT2);
        });
        // Refresh dynamic panels
        if (name.equals("dashboard"))    refreshDashboard();
        if (name.equals("transactions")) refreshTransactions();
        if (name.equals("loans"))        refreshLoans();
        if (name.equals("transfer"))     refreshTransferCombos();
        if (name.equals("settings"))     refreshSettings();
    }

    //  DASHBOARD
    private JPanel accCardsPanel;
    private JLabel lblTotalAssets, lblMonthIncome, lblMonthSpend, lblNetFlow;
    private JPanel recentTxPanel;

    private JPanel buildDashboard() {
        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBackground(UITheme.BG);
        root.setBorder(new EmptyBorder(16,16,16,16));

        accCardsPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        accCardsPanel.setOpaque(false);

        // Stats row
        JPanel statsRow = new JPanel(new GridLayout(1, 4, 10, 0));
        statsRow.setOpaque(false);
        lblTotalAssets = makeStat(statsRow, "Total Assets",  "Rs.0.00", UITheme.ACCENT);
        lblMonthIncome = makeStat(statsRow, "Month Income",  "Rs.0.00", UITheme.GREEN);
        lblMonthSpend  = makeStat(statsRow, "Month Spent",   "Rs.0.00", UITheme.RED);
        lblNetFlow     = makeStat(statsRow, "Net Flow",      "Rs.0.00", UITheme.AMBER);

        JPanel topPart = new JPanel(new BorderLayout(0,12));
        topPart.setOpaque(false);
        topPart.add(accCardsPanel, BorderLayout.NORTH);
        topPart.add(statsRow,      BorderLayout.CENTER);

        // Quick actions
        JPanel qaPanel = buildQuickActions();

        // Recent transactions
        JPanel recentWrap = new StyledPanel();
        recentWrap.setLayout(new BorderLayout());
        recentWrap.add(new Label("Recent Transactions", UITheme.MONO_BOLD, UITheme.TEXT2), BorderLayout.NORTH);
        recentTxPanel = new JPanel();
        recentTxPanel.setLayout(new BoxLayout(recentTxPanel, BoxLayout.Y_AXIS));
        recentTxPanel.setBackground(UITheme.BG2);
        recentWrap.add(new JScrollPane(recentTxPanel), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(1,2,12,0));
        bottom.setOpaque(false);
        bottom.add(qaPanel);
        bottom.add(recentWrap);

        root.add(topPart, BorderLayout.NORTH);
        root.add(bottom,  BorderLayout.CENTER);
        return root;
    }

    private JLabel makeStat(JPanel parent, String label, String val, Color color) {
        JPanel card = new StyledPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel v = new JLabel(val);
        v.setFont(new Font("Monospaced", Font.BOLD, 18));
        v.setForeground(color);
        JLabel l = new JLabel(label);
        l.setFont(UITheme.SMALL); l.setForeground(UITheme.TEXT2);
        card.add(v); card.add(l);
        parent.add(card);
        return v;
    }

    private JComboBox<String> qaAccCombo;
    private JTextField qaAmount, qaNote;
    private JLabel qaAlert;

    private JPanel buildQuickActions() {
        JPanel p = new StyledPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(new Label("Quick Actions", UITheme.MONO_BOLD, UITheme.TEXT2));
        p.add(Box.createVerticalStrut(10));

        qaAccCombo = new StyledCombo();
        qaAmount   = new StyledField(12);
        qaNote     = new StyledField(12);
        qaAlert    = new Label("", UITheme.SMALL, UITheme.GREEN);

        addFormRow(p, "Account", qaAccCombo);
        addFormRow(p, "Amount (Rs.)", qaAmount);
        addFormRow(p, "Note", qaNote);
        p.add(qaAlert);
        p.add(Box.createVerticalStrut(8));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        JButton depBtn = new StyledButton("+ Deposit",  UITheme.ACCENT, UITheme.BG);
        JButton wdBtn  = new StyledButton("- Withdraw", UITheme.RED,    Color.WHITE);
        depBtn.addActionListener(e -> doDeposit());
        wdBtn.addActionListener(e  -> doWithdraw());
        btns.add(depBtn); btns.add(wdBtn);
        p.add(btns);
        return p;
    }

    private void doDeposit() {
        try {
            BankAccount acc = selectedAccount(qaAccCombo);
            double amt = parseAmount(qaAmount.getText());
            String note = qaNote.getText().trim().isEmpty() ? "Deposit" : qaNote.getText().trim();
            acc.deposit(amt, note);
            qaNote.setText(""); qaAmount.setText("");
            showMsg(qaAlert, "Deposited Rs." + String.format("%.2f", amt), UITheme.GREEN);
            refreshDashboard();
        } catch (Exception ex) { showMsg(qaAlert, ex.getMessage(), UITheme.RED); }
    }

    private void doWithdraw() {
        try {
            BankAccount acc = selectedAccount(qaAccCombo);
            double amt = parseAmount(qaAmount.getText());
            String note = qaNote.getText().trim().isEmpty() ? "Withdrawal" : qaNote.getText().trim();
            acc.withdraw(amt, note);
            qaNote.setText(""); qaAmount.setText("");
            showMsg(qaAlert, "Withdrew Rs." + String.format("%.2f", amt), UITheme.RED);
            refreshDashboard();
        } catch (Exception ex) { showMsg(qaAlert, ex.getMessage(), UITheme.RED); }
    }

    private void refreshDashboard() {
        // Rebuild account cards
        accCardsPanel.removeAll();
        for (BankAccount acc : bank.getAccounts())
            accCardsPanel.add(new AccountCard(acc));
        accCardsPanel.revalidate(); accCardsPanel.repaint();

        // Stats
        double total = bank.getTotalAssets();
        long cutoff  = System.currentTimeMillis() - 30L * 86400000;
        List<Transaction> month = bank.getAllTransactions().stream()
                .filter(t -> t.getDate().getTime() > cutoff).collect(Collectors.toList());
        double income = month.stream().filter(Transaction::isCredit).mapToDouble(Transaction::getAmount).sum();
        double spent  = month.stream().filter(t -> !t.isCredit()).mapToDouble(Transaction::getAmount).sum();
        double net    = income - spent;

        lblTotalAssets.setText(String.format("Rs.%,.2f", total));
        lblMonthIncome.setText(String.format("Rs.%,.2f", income));
        lblMonthSpend.setText (String.format("Rs.%,.2f", spent));
        lblNetFlow.setText    (String.format("Rs.%,.2f", net));
        lblNetFlow.setForeground(net >= 0 ? UITheme.GREEN : UITheme.RED);

        // Populate combo
        refreshCombo(qaAccCombo);

        // Recent transactions
        recentTxPanel.removeAll();
        List<Transaction> recent = bank.getAllTransactions().stream().limit(10).collect(Collectors.toList());
        for (Transaction t : recent) recentTxPanel.add(makeTxRow(t));
        recentTxPanel.revalidate(); recentTxPanel.repaint();
    }

    private JPanel makeTxRow(Transaction t) {
        JPanel row = new JPanel(new BorderLayout(8,0));
        row.setBackground(UITheme.BG3);
        row.setBorder(new EmptyBorder(7,10,7,10));
        row.setMaximumSize(new Dimension(10000,46));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm");
        JLabel desc = new JLabel(t.getDescription());
        desc.setFont(UITheme.MONO); desc.setForeground(UITheme.TEXT);
        JLabel sub = new JLabel(sdf.format(t.getDate()));
        sub.setFont(UITheme.SMALL); sub.setForeground(UITheme.TEXT3);
        JPanel left = new JPanel(new GridLayout(2,1));
        left.setOpaque(false); left.add(desc); left.add(sub);

        JLabel amt = new JLabel((t.isCredit()?"+":"-") + String.format("Rs.%,.2f", t.getAmount()));
        amt.setFont(UITheme.MONO_BOLD);
        amt.setForeground(t.isCredit() ? UITheme.GREEN : UITheme.RED);

        row.add(left, BorderLayout.CENTER);
        row.add(amt,  BorderLayout.EAST);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false); wrap.setBorder(new EmptyBorder(0,0,4,0));
        wrap.add(row, BorderLayout.CENTER);
        return wrap;
    }

    //  TRANSACTIONS
    private TxTableModel  txModel;
    private JComboBox<String> filterAcc, filterType;

    private JPanel buildTransactions() {
        JPanel root = new JPanel(new BorderLayout(0,10));
        root.setBackground(UITheme.BG);
        root.setBorder(new EmptyBorder(16,16,16,16));

        // Filters
        JPanel filters = new StyledPanel();
        filters.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filters.add(new Label("Account:", UITheme.SMALL, UITheme.TEXT2));
        filterAcc = new StyledCombo();
        filters.add(filterAcc);
        filters.add(new Label("  Type:", UITheme.SMALL, UITheme.TEXT2));
        filterType = new StyledCombo();
        filterType.addItem("All Types");
        for (Transaction.Type tp : Transaction.Type.values())
            filterType.addItem(tp.name().replace("_"," "));
        filters.add(filterType);
        JButton applyFilter = new StyledButton("Apply", UITheme.BG3, UITheme.TEXT);
        applyFilter.addActionListener(e -> refreshTransactions());
        filters.add(applyFilter);

        txModel = new TxTableModel();
        JTable table = new JTable(txModel);
        table.setFont(UITheme.MONO); table.setRowHeight(28);
        table.setBackground(UITheme.BG2); table.setForeground(UITheme.TEXT);
        table.setGridColor(UITheme.BG3);
        table.getTableHeader().setFont(UITheme.MONO_BOLD);
        table.getTableHeader().setBackground(UITheme.BG3);
        table.getTableHeader().setForeground(UITheme.TEXT2);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setBackground(sel ? UITheme.BG3 : UITheme.BG2);
                setForeground(UITheme.TEXT);
                if (c == 4) {
                    String s = v.toString();
                    setForeground(s.startsWith("+") ? UITheme.GREEN : UITheme.RED);
                }
                setBorder(new EmptyBorder(0,8,0,8));
                return this;
            }
        });

        root.add(filters, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        return root;
    }

    private void refreshTransactions() {
        refreshCombo(filterAcc);
        List<Transaction> txs = bank.getAllTransactions();
        String selAcc  = (String) filterAcc.getSelectedItem();
        String selType = (String) filterType.getSelectedItem();
        if (selAcc != null && !selAcc.equals("All Accounts") && comboIdMap.containsKey(selAcc)) {
            String accId = comboIdMap.get(selAcc);
            txs = bank.getAccounts().stream()
                    .filter(a -> a.getAccountId().equals(accId))
                    .flatMap(a -> a.getTransactions().stream())
                    .sorted(Comparator.comparing(Transaction::getDate).reversed())
                    .collect(Collectors.toList());
        }
        if (selType != null && !selType.equals("All Types")) {
            String typeStr = selType.replace(" ", "_");
            txs = txs.stream().filter(t -> t.getType().name().equals(typeStr)).collect(Collectors.toList());
        }
        txModel.setData(txs);
    }

    //  TRANSFER
    private JComboBox<String> trFrom, trTo;
    private JTextField trAmount, trNote;
    private JLabel trAlert;

    private JPanel buildTransfer() {
        JPanel root = new JPanel(new GridLayout(1, 2, 12, 0));
        root.setBackground(UITheme.BG);
        root.setBorder(new EmptyBorder(16,16,16,16));

        // Internal transfer
        JPanel left = new StyledPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(new Label("Internal Transfer", UITheme.MONO_BOLD, UITheme.TEXT2));
        left.add(Box.createVerticalStrut(12));
        trFrom   = new StyledCombo();
        trTo     = new StyledCombo();
        trAmount = new StyledField(12);
        trNote   = new StyledField(12);
        trAlert  = new Label("", UITheme.SMALL, UITheme.GREEN);
        addFormRow(left, "From Account", trFrom);
        addFormRow(left, "To Account",   trTo);
        addFormRow(left, "Amount (Rs.)",   trAmount);
        addFormRow(left, "Memo",         trNote);
        left.add(trAlert);
        left.add(Box.createVerticalStrut(10));
        JButton doTr = new StyledButton("Execute Transfer", UITheme.ACCENT, UITheme.BG);
        doTr.setAlignmentX(LEFT_ALIGNMENT);
        doTr.addActionListener(e -> doTransfer());
        left.add(doTr);

        // Apply interest
        JPanel right = new StyledPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(new Label("Apply Monthly Interest", UITheme.MONO_BOLD, UITheme.TEXT2));
        right.add(Box.createVerticalStrut(6));
        JLabel info = new Label("Rate: 3.5% APY → 0.292%/month", UITheme.SMALL, UITheme.TEXT2);
        right.add(info);
        right.add(Box.createVerticalStrut(12));
        JLabel intAlert = new Label("", UITheme.SMALL, UITheme.GREEN);
        right.add(intAlert);
        JButton intBtn = new StyledButton("Apply Interest to All Savings", UITheme.AMBER, UITheme.BG);
        intBtn.setAlignmentX(LEFT_ALIGNMENT);
        intBtn.addActionListener(e -> {
            double total = bank.applyAllInterest();
            showMsg(intAlert, String.format("Applied Rs.%.2f interest!", total), UITheme.GREEN);
            refreshDashboard();
        });
        right.add(Box.createVerticalStrut(10));
        right.add(intBtn);

        root.add(left); root.add(right);
        return root;
    }

    private void doTransfer() {
        try {
            String fromId = accIdFromCombo(trFrom);
            String toId   = accIdFromCombo(trTo);
            double amt    = parseAmount(trAmount.getText());
            String memo   = trNote.getText().trim();
            bank.transfer(fromId, toId, amt, memo);
            trAmount.setText(""); trNote.setText("");
            showMsg(trAlert, String.format("Transferred Rs.%.2f successfully!", amt), UITheme.GREEN);
            refreshDashboard();
        } catch (Exception ex) { showMsg(trAlert, ex.getMessage(), UITheme.RED); }
    }

    private void refreshTransferCombos() { refreshCombo(trFrom); refreshCombo(trTo); }

    //  LOANS
    private JPanel loanListPanel;
    private JComboBox<String> lnType, lnAccount;
    private JTextField lnAmount, lnTerm;
    private JLabel lnAlert;

    private JPanel buildLoans() {
        JPanel root = new JPanel(new GridLayout(1,2,12,0));
        root.setBackground(UITheme.BG);
        root.setBorder(new EmptyBorder(16,16,16,16));

        // Loan list
        JPanel left = new StyledPanel();
        left.setLayout(new BorderLayout());
        left.add(new Label("Active Loans", UITheme.MONO_BOLD, UITheme.TEXT2), BorderLayout.NORTH);
        loanListPanel = new JPanel();
        loanListPanel.setLayout(new BoxLayout(loanListPanel, BoxLayout.Y_AXIS));
        loanListPanel.setBackground(UITheme.BG2);
        left.add(new JScrollPane(loanListPanel), BorderLayout.CENTER);

        // Apply loan
        JPanel right = new StyledPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(new Label("Apply for New Loan", UITheme.MONO_BOLD, UITheme.TEXT2));
        right.add(Box.createVerticalStrut(12));
        lnType    = new StyledCombo();
        lnAccount = new StyledCombo();
        lnAmount  = new StyledField(12);
        lnTerm    = new StyledField(12);
        lnAlert   = new Label("", UITheme.SMALL, UITheme.GREEN);
        lnType.addItem("Personal Loan (8% APR)");
        lnType.addItem("Auto Loan (5.5% APR)");
        lnType.addItem("Mortgage (6.8% APR)");
        addFormRow(right, "Loan Type",    lnType);
        addFormRow(right, "Deposit to",   lnAccount);
        addFormRow(right, "Amount (Rs.)",   lnAmount);
        addFormRow(right, "Term (months)",lnTerm);
        right.add(lnAlert);
        right.add(Box.createVerticalStrut(10));
        JButton apply = new StyledButton("Apply for Loan", UITheme.ACCENT, UITheme.BG);
        apply.setAlignmentX(LEFT_ALIGNMENT);
        apply.addActionListener(e -> doApplyLoan());
        right.add(apply);

        root.add(left); root.add(right);
        return root;
    }

    private void doApplyLoan() {
        try {
            Loan.LoanType type = switch (lnType.getSelectedIndex()) {
                case 1 -> Loan.LoanType.AUTO;
                case 2 -> Loan.LoanType.MORTGAGE;
                default-> Loan.LoanType.PERSONAL;
            };
            double amt = parseAmount(lnAmount.getText());
            int term   = Integer.parseInt(lnTerm.getText().trim());
            String accId = accIdFromCombo(lnAccount);
            Loan loan  = bank.applyLoan(type, amt, term, accId);
            lnAmount.setText(""); lnTerm.setText("");
            showMsg(lnAlert, String.format("Loan approved! Monthly: Rs.%.2f", loan.getMonthlyPayment()), UITheme.GREEN);
            refreshLoans(); refreshDashboard();
        } catch (Exception ex) { showMsg(lnAlert, ex.getMessage(), UITheme.RED); }
    }

    private void refreshLoans() {
        refreshCombo(lnAccount);
        loanListPanel.removeAll();
        if (bank.getLoans().isEmpty()) {
            loanListPanel.add(new Label("  No active loans.", UITheme.MONO, UITheme.TEXT3));
        }
        for (Loan loan : bank.getLoans()) {
            loanListPanel.add(makeLoanCard(loan));
            loanListPanel.add(Box.createVerticalStrut(8));
        }
        loanListPanel.revalidate(); loanListPanel.repaint();
    }

    private JPanel makeLoanCard(Loan loan) {
        JPanel card = new JPanel(new BorderLayout(0,6));
        card.setBackground(UITheme.BG3);
        card.setBorder(new EmptyBorder(12,14,12,14));
        card.setMaximumSize(new Dimension(10000, 140));

        // Header
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        JLabel name = new JLabel(loan.getLoanType().name() + " Loan  " + loan.getLoanId());
        name.setFont(UITheme.MONO_BOLD); name.setForeground(UITheme.TEXT);
        JLabel rate = new JLabel(String.format("%.1f%% APR", loan.getAnnualRate()*100));
        rate.setFont(UITheme.SMALL); rate.setForeground(UITheme.AMBER);
        hdr.add(name, BorderLayout.WEST); hdr.add(rate, BorderLayout.EAST);

        // Info row
        JPanel info = new JPanel(new GridLayout(1,3,8,0));
        info.setOpaque(false);
        addMiniStat(info,"Principal",  String.format("Rs.%,.2f", loan.getPrincipal()),   UITheme.TEXT);
        addMiniStat(info,"Remaining",  String.format("Rs.%,.2f", loan.getRemainingBalance()), UITheme.RED);
        addMiniStat(info,"Monthly",    String.format("Rs.%,.2f", loan.getMonthlyPayment()),    UITheme.AMBER);

        // Progress
        int pct = loan.getProgressPct();
        JProgressBar bar = new JProgressBar(0,100);
        bar.setValue(pct);
        bar.setString(loan.getPaymentsMade() + "/" + loan.getTermMonths() + " payments  (" + pct + "%)");
        bar.setStringPainted(true);
        bar.setForeground(UITheme.ACCENT);
        bar.setBackground(UITheme.BG);
        bar.setFont(UITheme.SMALL);

        JButton pay = new StyledButton(
                loan.isPaidOff() ? "PAID OFF" : String.format("Pay Rs.%.2f", loan.getMonthlyPayment()),
                loan.isPaidOff() ? UITheme.BG3 : UITheme.AMBER, UITheme.BG);
        pay.setEnabled(!loan.isPaidOff());
        pay.addActionListener(e -> {
            try {
                bank.makeLoanPayment(loan.getLoanId());
                refreshLoans(); refreshDashboard();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Payment Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        card.add(hdr,  BorderLayout.NORTH);
        card.add(info, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0,4));
        bottom.setOpaque(false);
        bottom.add(bar, BorderLayout.CENTER);
        bottom.add(pay, BorderLayout.EAST);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private void addMiniStat(JPanel p, String lbl, String val, Color color) {
        JPanel c = new JPanel(new GridLayout(2,1));
        c.setOpaque(false);
        JLabel v = new JLabel(val); v.setFont(UITheme.MONO_BOLD); v.setForeground(color);
        JLabel l = new JLabel(lbl); l.setFont(UITheme.SMALL);     l.setForeground(UITheme.TEXT3);
        c.add(v); c.add(l); p.add(c);
    }

    //  SETTINGS
    private JTextField setName;
    private JComboBox<String> newAccType;
    private JTextField newAccNick;
    private JLabel setAlert;
    private JPanel settingsAccPanel;

    private JPanel buildSettings() {
        JPanel root = new JPanel(new GridLayout(1,2,12,0));
        root.setBackground(UITheme.BG);
        root.setBorder(new EmptyBorder(16,16,16,16));

        JPanel left = new StyledPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(new Label("Account Holder", UITheme.MONO_BOLD, UITheme.TEXT2));
        left.add(Box.createVerticalStrut(10));
        setName = new StyledField(16);
        setName.setText(bank.getCustomerName());
        addFormRow(left, "Name", setName);
        JButton saveName = new StyledButton("Save Name", UITheme.ACCENT, UITheme.BG);
        saveName.setAlignmentX(LEFT_ALIGNMENT);
        saveName.addActionListener(e -> {
            bank.setCustomerName(setName.getText().trim());
            JOptionPane.showMessageDialog(this, "Name updated!", "Saved", JOptionPane.INFORMATION_MESSAGE);
        });
        left.add(saveName);
        left.add(Box.createVerticalStrut(24));

        left.add(new Label("Open New Account", UITheme.MONO_BOLD, UITheme.TEXT2));
        left.add(Box.createVerticalStrut(10));
        newAccType = new StyledCombo();
        newAccType.addItem("Checking Account");
        newAccType.addItem("Savings Account (3.5% APY)");
        newAccType.addItem("Investment Account");
        newAccNick = new StyledField(16);
        setAlert   = new Label("", UITheme.SMALL, UITheme.GREEN);
        addFormRow(left, "Account Type", newAccType);
        addFormRow(left, "Nickname",     newAccNick);
        left.add(setAlert);
        left.add(Box.createVerticalStrut(8));
        JButton openAcc = new StyledButton("Open Account", UITheme.ACCENT, UITheme.BG);
        openAcc.setAlignmentX(LEFT_ALIGNMENT);
        openAcc.addActionListener(e -> doOpenAccount());
        left.add(openAcc);

        JPanel right = new StyledPanel();
        right.setLayout(new BorderLayout());
        right.add(new Label("Account Summary", UITheme.MONO_BOLD, UITheme.TEXT2), BorderLayout.NORTH);
        settingsAccPanel = new JPanel();
        settingsAccPanel.setLayout(new BoxLayout(settingsAccPanel, BoxLayout.Y_AXIS));
        settingsAccPanel.setBackground(UITheme.BG2);
        right.add(new JScrollPane(settingsAccPanel), BorderLayout.CENTER);

        root.add(left); root.add(right);
        return root;
    }

    private void doOpenAccount() {
        String nick = newAccNick.getText().trim();
        if (nick.isEmpty()) { showMsg(setAlert, "Enter an account nickname.", UITheme.RED); return; }
        BankAccount acc = switch (newAccType.getSelectedIndex()) {
            case 1 -> new SavingsAccount(nick);
            case 2 -> new InvestmentAccount(nick);
            default-> new CheckingAccount(nick);
        };
        bank.addAccount(acc);
        newAccNick.setText("");
        showMsg(setAlert, nick + " account opened!", UITheme.GREEN);
        refreshSettings(); refreshDashboard();
    }

    private void refreshSettings() {
        settingsAccPanel.removeAll();
        for (BankAccount acc : bank.getAccounts()) {
            JPanel row = new JPanel(new BorderLayout(8,0));
            row.setBackground(UITheme.BG3);
            row.setBorder(new EmptyBorder(8,12,8,12));
            row.setMaximumSize(new Dimension(10000,52));
            JLabel name = new JLabel(acc.getNickname() + "  " + acc.getAccountId());
            name.setFont(UITheme.MONO); name.setForeground(UITheme.TEXT);
            JLabel type = new JLabel(acc.getTypeLabel());
            type.setFont(UITheme.SMALL); type.setForeground(acc.getThemeColor());
            JPanel info = new JPanel(new GridLayout(2,1));
            info.setOpaque(false); info.add(name); info.add(type);
            JLabel bal = new JLabel(String.format("Rs.%,.2f", acc.getBalance()));
            bal.setFont(UITheme.MONO_BOLD); bal.setForeground(UITheme.ACCENT);
            row.add(info, BorderLayout.CENTER);
            row.add(bal,  BorderLayout.EAST);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false); wrap.setBorder(new EmptyBorder(0,0,4,0));
            wrap.add(row, BorderLayout.CENTER);
            settingsAccPanel.add(wrap);
        }
        settingsAccPanel.revalidate(); settingsAccPanel.repaint();
    }

    //  HELPERS

    private void addFormRow(JPanel p, String label, JComponent field) {
        JLabel lbl = new Label(label.toUpperCase(), UITheme.SMALL, UITheme.TEXT2);
        lbl.setBorder(new EmptyBorder(8,0,4,0));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        field.setAlignmentX(LEFT_ALIGNMENT);
        if (field instanceof JComboBox || field instanceof JTextField)
            field.setMaximumSize(new Dimension(10000, 36));
        p.add(lbl); p.add(field);
    }

    // Maps each combo display string → accountId so we never parse text to extract the ID
    private final Map<String, String> comboIdMap = new HashMap<>();

    private void refreshCombo(JComboBox<String> combo) {
        if (combo == null) return;
        // Remember which accountId was selected before rebuilding
        String prevId = null;
        String prevSel = (String) combo.getSelectedItem();
        if (prevSel != null) prevId = comboIdMap.get(prevSel);

        combo.removeAllItems();
        combo.addItem("All Accounts");
        comboIdMap.clear();

        String restoreSel = null;
        for (BankAccount a : bank.getAccounts()) {
            String display = a.getNickname() + " [" + a.getAccountId() + "]  Rs." +
                    String.format("%,.2f", a.getBalance());
            comboIdMap.put(display, a.getAccountId());
            combo.addItem(display);
            if (a.getAccountId().equals(prevId)) restoreSel = display;
        }
        if (restoreSel != null) combo.setSelectedItem(restoreSel);
    }

    private BankAccount selectedAccount(JComboBox<String> combo) throws NoSuchElementException {
        String sel = (String) combo.getSelectedItem();
        if (sel == null || sel.equals("All Accounts") || !comboIdMap.containsKey(sel))
            throw new NoSuchElementException("No account selected.");
        return bank.findAccount(comboIdMap.get(sel));
    }

    private String accIdFromCombo(JComboBox<String> combo) {
        String sel = (String) combo.getSelectedItem();
        if (sel == null || !comboIdMap.containsKey(sel)) return "";
        return comboIdMap.get(sel);
    }

    private double parseAmount(String text) throws InvalidAmountException {
        try {
            double v = Double.parseDouble(text.trim());
            if (v <= 0) throw new InvalidAmountException("Amount must be positive.");
            return v;
        } catch (NumberFormatException e) { throw new InvalidAmountException("Enter a valid numeric amount."); }
    }

    private void showMsg(JLabel lbl, String msg, Color color) {
        lbl.setText(msg); lbl.setForeground(color);
        javax.swing.Timer t = new javax.swing.Timer(3000, e -> lbl.setText(""));
        t.setRepeats(false); t.start();
    }

    //  ENTRY POINT
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new BankingSystem().setVisible(true);
        });
    }
}