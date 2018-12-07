import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in , out);
		out.close();
	}
	
	private int[] readForCounts(BitInputStream in) {
		/**Create an integer array that can store 257 values (use ALPH_SIZE + 1). 
		 * You'll read 8-bit characters/chunks, (using BITS_PER_WORD rather than 8), 
		 * and use the read/8-bit value as an index into the array, incrementing the 
		 * frequency. The code you start with in compress (and decompress) illustrates 
		 * how to read until the sentinel -1 is read to indicate there are no more 
		 * bits in the input stream. You'll need explicitly set freq[PSEUDO_EOF] = 1 
		 * for the array to indicate there is one occurrence of the value PSEUDO_EOF. 
		 */
		
		int[] counts = new int[ALPH_SIZE + 1];
        int c;
        while ((c = in.readBits(BITS_PER_WORD)) != -1) {
            counts[c]++;
        }
        counts[PSEUDO_EOF] = 1;
        return counts;
	}
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
        if (myDebugLevel == DEBUG_HIGH) {
            System.out.printf("%5s %10s\n", "chunks", "freq");
        }
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                pq.add(new HuffNode(i, counts[i], null, null));
                if (myDebugLevel == DEBUG_HIGH) {
                    System.out.printf("%5s %10s\n", i, counts[i]);
                }
            }
        }
        if (myDebugLevel == DEBUG_HIGH) {
            System.out.println(
                    "Priority Queue created with " + pq.size() + " nodes");
        }
        while (pq.size() > 1) {
            HuffNode left = pq.remove();
            HuffNode right = pq.remove();
            HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left,
                    right);
            pq.add(t);
        }
        return pq.remove();
    }
	
	private String[] makeCodingsFromTree(HuffNode root) {
		 String[] encodings = new String[ALPH_SIZE + 1];
		 codingHelper(root, "", encodings);
		 return encodings;

	}
	
	//fix this blue shit
	/**If the root is not a leaf, you'll need to make recursive calls 
	  * adding "0" to the path when making a recursive call on the left 
	  * subtree and adding "1" to the path when making a recursive call on the right subtree. 
	  * Every node in a Huffman tree has two children.
	  */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		 if (root.myLeft == null && root.myRight == null) {
		        encodings[root.myValue] = path;
		        if (myDebugLevel >= DEBUG_HIGH) {
	                System.out.printf("Encoding for %3d is %s\n", root.myValue,
	                        path);
	            }
		 } else {
			 codingHelper(root.myLeft, path + "0", encodings);
			 codingHelper(root.myRight, path + "1", encodings);
		 }
	}
	
	private void writeHeader(HuffNode root, BitOutputStream out) {
		/**Else, if the node is a leaf, write a single bit of one, followed by nine bits of 
		 * the value stored in the leaf.  This is a pre-order traversal: write one bit for the node, 
		 * then make two recursive calls if the node is an internal node. No recursion is used for leaf nodes.
		 * You'll need to write 9 bits, or BITS_PER_WORD + 1, because there are possibly 257 values including PSEUDO_EOF.
		 */
		
		if (root.myLeft == null && root.myRight == null) {
            out.writeBits(1, 1);
            out.writeBits(BITS_PER_WORD + 1, root.myValue);
            if (myDebugLevel == DEBUG_HIGH) {
                System.out.println("wrote leaf for tree: " + root.myValue);
            }
        } else {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out); 
			writeHeader(root.myRight, out); 
	   }
	}
	
	private void writeCompressedBits(String [] codings, BitInputStream in, BitOutputStream out) {
		int c;
        while ((c = in.readBits(BITS_PER_WORD)) != -1) {
            String code = codings[c];
            out.writeBits(code.length(), Integer.parseInt(code, 2));
            if (myDebugLevel == DEBUG_HIGH) {
                System.out.println("wrote " + code.length() + " bits: " + code);
            }
        }
        String code = codings[PSEUDO_EOF];
        if (myDebugLevel == DEBUG_HIGH) {
            System.out.println("wrote EOF");
        }
        out.writeBits(code.length(), Integer.parseInt(code, 2));
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " + bits);
		}
		
		if (bits == -1) {
			throw new HuffException("reader failed");
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in , out);
		out.close();
	}

	private HuffNode readTreeHeader(BitInputStream in) {
	int bit = in.readBits(1);
	while (bit != PSEUDO_EOF) {
		if (bit == -1) throw new HuffException("reader failed");
		if (bit == 0) {
		    HuffNode left = readTreeHeader(in);
		    HuffNode right = readTreeHeader(in);
		    return new HuffNode(0, 0, left, right);
		}
		else {
		    int value = in.readBits(BITS_PER_WORD + 1);
		    return new HuffNode(value, 0, null, null);
		}
	}
		return null;

	}
	
	private void readCompressedBits(HuffNode root, BitInputStream in , BitOutputStream out) {
	 HuffNode current = root; 
	   while (true) {
	       int bits = in.readBits(1);
	       if (bits == -1) {
	           throw new HuffException("bad input, no PSEUDO_EOF");
	       }
	       else { 
	           if (bits == 0) current = current.myLeft;
	           else current = current.myRight;

	           if (current.myLeft == null && current.myRight == null) {
	               if (current.myValue == PSEUDO_EOF) 
	                   break;   // out of loop
	               else {
	                   out.writeBits(BITS_PER_WORD, current.myValue);
	                   current = root; // start back after leaf
	               }
	           }
	       }
	   }
	}
}
	       
	   
