// Address provider for dynamic address generation
public class DynamicAddressProvider implements AddressProvider {
    private final Account baseAccount;
    private final NetworkInfo network;
    private final AtomicInteger addressIndex = new AtomicInteger(0);
    
    public DynamicAddressProvider(Account baseAccount, NetworkInfo network) {
        this.baseAccount = baseAccount;
        this.network = network;
    }
    
    @Override
    public String getAddress() {
        try {
            // Generate next derived address
            int index = addressIndex.getAndIncrement();
            Account derivedAccount = baseAccount.derive(index);
            
            Address address = AddressProvider.getEntAddress(
                derivedAccount.hdKeyPair().getPublicKey(),
                network
            );
            
            String addressBech32 = address.toBech32();
            System.out.println("Generated address " + index + ": " + addressBech32);
            
            return addressBech32;
        } catch (Exception e) {
            System.err.println("Address generation failed: " + e.getMessage());
            // Fallback to base account address
            return baseAccount.baseAddress();
        }
    }
    
    @Override
    public String getAddress(int index) {
        try {
            Account derivedAccount = baseAccount.derive(index);
            Address address = AddressProvider.getEntAddress(
                derivedAccount.hdKeyPair().getPublicKey(),
                network
            );
            
            return address.toBech32();
        } catch (Exception e) {
            System.err.println("Specific address generation failed: " + e.getMessage());
            throw new RuntimeException("Failed to generate address at index " + index, e);
        }
    }
    
    public int getCurrentIndex() {
        return addressIndex.get();
    }
    
    public void resetIndex() {
        addressIndex.set(0);
    }
}