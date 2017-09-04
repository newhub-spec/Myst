package mpc;

import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.KeyPair;

/**
 *
 * @author Vasilios Mavroudis and Petr Svenda
 */
public class QuorumContext {
    // Keys
    //public DKG KeyPair;

    public short CARD_INDEX_THIS = 0;   // index of player realised by this card
    public short NUM_PLAYERS = 0;       // current number of players
    private Player[] players = null;      // contexts for all players (including this card)

    public static boolean SETUP = false; // Have the scheme parameters been set?

    // Signing
    public Bignat signature_counter = null;
    public byte[] secret_seed = null; // = { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};

    // Moved from DKG begin
    boolean PLAYERS_IN_RAM = true; // if true, player (participant) info is stored in RAM => faster, consuming RAM and will NOT survive card reset
    boolean COMPUTE_Y_ONTHEFLY = true; // TODO: Make on-the-fly computation only option and make code simpler
    static boolean IS_BACKDOORED_EXAMPLE = false; // if true, then applet will not follow protocol but generates backdoored applet instead
    //private byte[] privbytes = {(byte)0xB3, (byte)0x46, (byte)0x67, (byte)0x55, (byte)0x18, (byte)0x08, (byte)0x46, (byte)0x23, (byte)0xBC, (byte)0x11, (byte)0x1C, (byte)0xC5, (byte)0x3F, (byte)0xF6, (byte)0x15, (byte)0xB1, (byte)0x52, (byte)0xA3, (byte)0xF6, (byte)0xD1, (byte)0x58, (byte)0x52, (byte)0x78, (byte)0x37, (byte)0x0F, (byte)0xA1, (byte)0xBA, (byte)0x0E, (byte)0xA1, (byte)0x60, (byte)0x23, (byte)0x7E};    
    public static final byte[] privbytes_backdoored = {(byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55};

    byte[] tmp_arr = null; // TODO: used as  array for temporary result -> move to resource manager


    ECCurve theCurve = null;
    private KeyPair pair = null;
    private byte[] x_i_Bn = null;           // share xi , which is a randomly sampled element from Zn
    private byte[] CARD_THIS_YS = null;     // Ys for this card  (not stored in Player[] context as shares are combined on the fly) 
    //private mpc.ECPointBase Y_EC = null; // TODO: remove if COMPUTE_Y_ONTHEFLY is only option
    private mpc.ECPointBase Y_EC_onTheFly = null; // aggregated Ys computed on the fly instead of in one shot once all shares are provided (COMPUTE_Y_ONTHEFLY)
    private short Y_EC_onTheFly_shares_count = 0; // number of public key shares already provided and combined during KeyGen_StorePublicKey
    // del private short times_x_used	= 0; //This should be erased, it's no longer needed.

    /*
     * 0 Publishes hash(Y_i) 1 Publishes hash(Y_i), Y_i 2 Publishes hash(Y_i),
     * Y_i, x_i, and Y -1 Error State (when hashes-pubs do not check out),
     * doesn't respond. Reset() is needed
     */
    // BUGBUG: Check thoroughly for all state transitions (automata-based programming)
    private short STATE = -1; // current state of the protocol run - some operations are not available in given state    
    // Moved from DKG end
    
    
    public QuorumContext(ECConfig eccfg, ECCurve curve) {
        signature_counter = new Bignat(Consts.SHARE_BASIC_SIZE, JCSystem.MEMORY_TYPE_TRANSIENT_RESET, eccfg.bnh);
        secret_seed = new byte[Consts.SECRET_SEED_SIZE];
        
        
        // Moved from DKG begin
        theCurve = curve;

        //GenPoint = ECPointBuilder.buildECPoint(ECPointBuilder.TYPE_EC_FP_POINT, (short) SecP256r1.KEY_LENGTH);
        //GenPoint.setW(SecP256r1.G, (short) 0, (short) SecP256r1.G.length);
        this.pair = theCurve.newKeyPair(this.pair);
        x_i_Bn = JCSystem.makeTransientByteArray(Consts.SHARE_BASIC_SIZE, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
        // del copy_x_i_Bn = JCSystem.makeTransientByteArray(Consts.SHARE_BASIC_SIZE, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
        tmp_arr = JCSystem.makeTransientByteArray(Consts.SHARE_DOUBLE_SIZE_CARRY, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);

        ///////////
        //Arrays//
        //////////
        players = new Player[Consts.MAX_NUM_PLAYERS];
        if (COMPUTE_Y_ONTHEFLY) {
            CARD_THIS_YS = JCSystem.makeTransientByteArray(Consts.SHARE_DOUBLE_SIZE_CARRY, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
        }
        for (short i = 0; i < Consts.MAX_NUM_PLAYERS; i++) {
            players[i] = new Player();
            if (PLAYERS_IN_RAM) {
                if (!COMPUTE_Y_ONTHEFLY) {
                    players[i].Ys = JCSystem.makeTransientByteArray(Consts.SHARE_DOUBLE_SIZE_CARRY, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
                }
                players[i].hash = JCSystem.makeTransientByteArray(Consts.SHARE_BASIC_SIZE, JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
            } else {
                players[i].Ys = new byte[Consts.SHARE_DOUBLE_SIZE_CARRY];
                players[i].hash = new byte[Consts.SHARE_BASIC_SIZE];
            }
        }

        //Y_EC = ECPointBuilder.createPoint(SecP256r1.KEY_LENGTH);
        //Y_EC.initializeECPoint_SecP256r1();
        Y_EC_onTheFly = ECPointBuilder.createPoint(SecP256r1.KEY_LENGTH);
        Y_EC_onTheFly.initializeECPoint_SecP256r1();

        STATE = 0;
        // Moved from DKG end
    }
    public void Reset() {
	NUM_PLAYERS = 0;
        CARD_INDEX_THIS = 0;     
        SETUP = false;
        signature_counter.zero();
        Invalidate(true);
    }
    
    short getState() {
        return STATE;
    }

    // ///////////////////////////////////////////////////////////////////
    // // Crypto functionality ////
    // //////////////////////////////////////////////////////////////////
    // State 0
    public final void Reset(short numPlayers, short cardID, boolean bPrepareDecryption) {
        if (numPlayers > Consts.MAX_NUM_PLAYERS) {
            ISOException.throwIt(Consts.SW_TOOMANYPLAYERS);
        }

        Invalidate(false);

        Y_EC_onTheFly_shares_count = 0;

        NUM_PLAYERS = numPlayers;
        CARD_INDEX_THIS = cardID;

        /* Gen x_i */
        /* unused 20170904        
         if ((bFastGen==false) || (times_x_used==0)) {
         */
        pair.genKeyPair();

        if (IS_BACKDOORED_EXAMPLE) {
                    // This branch demonstrates behavior of malicious attacker 
            // If enabled, key is not generated randomly as required per protocol, but fixed to vulnerable value instead
            ECPublicKey pub = (ECPublicKey) pair.getPublic();
            ECPrivateKey priv = (ECPrivateKey) pair.getPrivate();

            // Set "backdoored" (known) private key - all 0x55 ... 0x55
            priv.setS(privbytes_backdoored, (short) 0, (short) privbytes_backdoored.length);
            ((ECPrivateKey) pair.getPrivate()).getS(x_i_Bn, (short) 0);
                    // Compute and set corresponding public key (to backdoored private one)
            //CryptoOperations.placeholder.ScalarMultiplication(SecP256r1.G, (short) 0, (short) SecP256r1.G.length, privbytes_backdoored, tmp_arr);
            CryptoOperations.placeholder.ScalarMultiplication(CryptoOperations.GenPoint, privbytes_backdoored, tmp_arr);
            pub.setW(tmp_arr, (short) 0, (short) 65);
        } else {
            // Legitimate generation of key as per protocol by non-compromised participants
            ((ECPrivateKey) pair.getPrivate()).getS(x_i_Bn, (short) 0);
        }

        /* unused 20170904  	        
         Util.arrayCopyNonAtomic(x_i_Bn, (short)0, copy_x_i_Bn, (short)0, (short)x_i_Bn.length);

         } 
      
         else if ((bFastGen==true) && (times_x_used>0)){
         //Gen HMAC from existing x_i_Bn
         md.reset();
         md.update(copy_x_i_Bn, (short)0, (short)copy_x_i_Bn.length); //secret
         md.doFinal(x_i_Bn, (short) 0, (short) x_i_Bn.length, x_i_Bn, (short) 0); //and previous K(i)
         }
        
         times_x_used += 1;
         */
        //CryptoOperations.placeholder.ScalarMultiplication(SecP256r1.G, (short) 0, (short) SecP256r1.G.length, x_i_Bn, CARD_THIS_YS); // yG
        CryptoOperations.placeholder.ScalarMultiplication(CryptoOperations.GenPoint, x_i_Bn, CARD_THIS_YS); // yG

        if (COMPUTE_Y_ONTHEFLY) {
            Y_EC_onTheFly.setW(CARD_THIS_YS, (short) 0, (short) CARD_THIS_YS.length);
        }
        // Update stored x_i properties
        players[CARD_INDEX_THIS].bYsValid = true;
        Y_EC_onTheFly_shares_count++; // share for this card is included
        CryptoOperations.md.reset();
        CryptoOperations.md.doFinal(CARD_THIS_YS, (short) 0, Consts.SHARE_DOUBLE_SIZE_CARRY, players[CARD_INDEX_THIS].hash, (short) 0);
        players[CARD_INDEX_THIS].bHashValid = true;

        // Pre-prepare engine for faster Decrypt later
        if (bPrepareDecryption) {
            if (ECPointBase.ECMultiplHelperDecrypt != null) { // Use prepared engine - cards with native support for EC
                ECPointBase.disposable_privDecrypt.setS(x_i_Bn, (short) 0, (short) x_i_Bn.length);
                ECPointBase.ECMultiplHelperDecrypt.init(ECPointBase.disposable_privDecrypt);
            }
        }
        STATE = 0;
    }

    public short GetHash(byte[] array, short offset) {
        if (players[CARD_INDEX_THIS].bHashValid) {
            Util.arrayCopyNonAtomic(players[CARD_INDEX_THIS].hash, (short) 0, array, offset, (short) players[CARD_INDEX_THIS].hash.length);
            return (short) players[CARD_INDEX_THIS].hash.length;
        } else {
            ISOException.throwIt(Consts.SW_INVALIDHASH);
            return (short) -1;
        }
    }

    // State 0
    public void SetHash(short id, byte[] hash, short hashOffset, short hashLength) {
        if (id < 0 || id == CARD_INDEX_THIS || id >= NUM_PLAYERS) {
            ISOException.throwIt(Consts.SW_INVALIDPLAYERINDEX);
        }
        Util.arrayCopyNonAtomic(hash, hashOffset, players[id].hash, (short) 0, hashLength);
        players[id].bHashValid = true;
    }

    public void SetYs(short id, byte[] Y, short YOffset, short YLength) {
        if (COMPUTE_Y_ONTHEFLY) {
            if (players[id].bYsValid) {
                ISOException.throwIt(Consts.SW_SHAREALREADYSTORED);
            }
            // Verify against previously stored hash
            if (!players[id].bHashValid) {
                ISOException.throwIt(Consts.SW_INVALIDHASH);
            }
            if (!VerifyPair(Y, YOffset, YLength, players[id].hash)) {
                ISOException.throwIt(Consts.SW_INVALIDHASH);
            }

            // Directly add into Y_EC_onTheFly, no storage into RAM
            ECPointBase.ECPointAddition(Y_EC_onTheFly, Y, YOffset, Y_EC_onTheFly);
            players[id].bYsValid = true;
            Y_EC_onTheFly_shares_count++;

            if (Y_EC_onTheFly_shares_count == NUM_PLAYERS) {
                // check if shares for all players were included 
                for (short i = 0; i < NUM_PLAYERS; i++) {
                    if (!players[i].bYsValid) {
                        ISOException.throwIt(Consts.SW_INTERNALSTATEMISMATCH);
                    }
                }
                STATE = 2;
            }
        }
        /* unused 20170904        
         else {
         Util.arrayCopyNonAtomic(Y, YOffset, players[id].Ys, (short) 0, YLength);
         players[id].bYsValid = true;

         // Ready to move to state 2?
         Y_EC_onTheFly_shares_count = 0;
         for (short i = 0; i < N_PLAYERS; i++) {
         if (players[i].bYsValid) {
         Y_EC_onTheFly_shares_count += 1;
         }
         }

         if (Y_EC_onTheFly_shares_count == N_PLAYERS) {
         if (VerifyPairs() == true) {
         STATE = 2;

         // Compute aggregated Y
         Y_EC.setW(players[0].Ys, (short) 0, (short) players[0].Ys.length); 
         for (short i = 1; i < N_PLAYERS; i++) {
         ECPointBase.ECPointAddition(Y_EC, players[i].Ys, (short) 0, Y_EC);
         }

         // Y_EC now contains added points from all players
         }
         }
         }
         */
    }

    public short GetYi(byte[] array, short offset) {
        //If not on state 1 already:
        if (STATE < 1) {
            // Ready to move to state 1?
            short tmp_count = 0;
            for (short i = 0; i < NUM_PLAYERS; i++) {
                if (players[i].bHashValid) {
                    tmp_count += 1;
                }
            }

            if (tmp_count == NUM_PLAYERS) {
                STATE = 1;
            }
        }

        if (STATE >= 1) {
            if (players[CARD_INDEX_THIS].bYsValid) {
                Util.arrayCopyNonAtomic(CARD_THIS_YS, (short) 0, array, offset, (short) CARD_THIS_YS.length);
                return (short) CARD_THIS_YS.length;
            } else {
                ISOException.throwIt(Consts.SW_INVALIDYSHARE);
            }
        } else {
            ISOException.throwIt(Consts.SW_INCORRECTSTATE);
        }
        return 0;
    }

    // State 2
    public byte[] Getxi() { // Used to sign and decrypt
        if ((STATE >= 2) || (NUM_PLAYERS == 1)) {
            return x_i_Bn;
        } else {
            ISOException.throwIt(Consts.SW_INCORRECTSTATE);
            return null;
        }
    }

    public short Getxi(byte[] array, short offset) {
        if ((STATE >= 2) || (NUM_PLAYERS == 1)) {
            Util.arrayCopyNonAtomic(x_i_Bn, (short) 0, array, offset, (short) x_i_Bn.length);
            return (short) x_i_Bn.length;
        } else {
            return (short) -1;
        }
    }

    // State 2
    public ECPointBase GetY() {
        if ((STATE >= 2) || (NUM_PLAYERS == 1)) {
            if (COMPUTE_Y_ONTHEFLY) {
                return Y_EC_onTheFly;
            }
            /* unused 20170904              
             else {
             return Y_EC;
             }
             */
        }

        return null;
    }

    // State -1
    public void Invalidate(boolean bEraseAllArrays) {
        if (bEraseAllArrays) {
            Util.arrayFillNonAtomic(tmp_arr, (short) 0, (short) tmp_arr.length, (byte) 0);
            Util.arrayFillNonAtomic(x_i_Bn, (short) 0, (short) x_i_Bn.length, (byte) 0);
        }
        // Invalidate all items
        for (short i = 0; i < Consts.MAX_NUM_PLAYERS; i++) {
            players[i].bHashValid = false;
            players[i].bYsValid = false;
            if (bEraseAllArrays) {
                Util.arrayFillNonAtomic(players[i].hash, (short) 0, (short) players[i].hash.length, (byte) 0);
                if (players[i].Ys != null) {
                    Util.arrayFillNonAtomic(players[i].Ys, (short) 0, (short) players[i].Ys.length, (byte) 0);
                }
            }
        }

        STATE = -1;
        Y_EC_onTheFly_shares_count = 0;
    }

    // State -1
    // /////////////////////////
    // Helper Functions
    // ////////////////////////
    private boolean VerifyPairs() {
        for (short i = 0; i < NUM_PLAYERS; i++) {
            if (!VerifyPair(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean VerifyPair(short index) {
        if (!players[index].bHashValid || !players[index].bYsValid) {
            return false;
        } else {
            return VerifyPair(players[index].Ys, (short) 0, Consts.SHARE_DOUBLE_SIZE_CARRY, players[index].hash);
        }
    }

    private boolean VerifyPair(byte[] Ys, short YsOffset, short YsLength, byte[] hash) {
        CryptoOperations.md.reset();
        CryptoOperations.md.doFinal(Ys, YsOffset, YsLength, tmp_arr, (short) 0);
        if (Util.arrayCompare(tmp_arr, (short) 0, hash,
                (short) 0, Consts.SHARE_BASIC_SIZE) != 0) {
            return false;
        } else {
            return true;
        }
    }
    
}
